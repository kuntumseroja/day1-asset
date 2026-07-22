import express from 'express';
import { createServer } from 'http';
import { WebSocketServer } from 'ws';
import { readFileSync } from 'fs';
import { randomUUID } from 'crypto';
import { evaluatePolicy, fetchActiveCaps, checkPolicyHealth } from './policy-client.js';

const app = express();
const server = createServer(app);
const wss = new WebSocketServer({ server, path: '/api/v1/ws' });

app.use(express.json());

const PORT = process.env.PORT || 8083;
const SCRIPT_PATH = process.env.SCRIPT_PATH || './scripted-day.json';

const transactions = new Map();
const wsClients = new Set();
let replayIndex = 0;

const limits = {
  perIssuanceCap: 500_000_000,
  perIssuanceUsed: 0,
  dailyCumulativeCap: 5_000_000_000,
  dailyCumulativeUsed: 1_200_000_000,
  policySynced: false,
  policySource: 'default',
};

async function refreshCapsFromPolicy() {
  try {
    const caps = await fetchActiveCaps();
    limits.perIssuanceCap = caps.perIssuanceCap;
    limits.dailyCumulativeCap = caps.dailyCumulativeCap;
    limits.policySynced = true;
    limits.policySource = caps.source;
  } catch (e) {
    limits.policySynced = false;
    limits.policySource = 'fallback';
    console.warn('Policy caps unavailable:', e.message);
  }
}

function emitSettlementEvent(event) {
  const payload = {
    eventId: randomUUID(),
    eventType: event.eventType || 'STATUS_TRANSITION',
    uetr: event.uetr,
    participantId: event.participantId,
    transactionType: event.type || 'ISSUANCE',
    status: event.status,
    amount: event.amount,
    queuePosition: event.queuePosition ?? null,
    timestamp: new Date().toISOString(),
    duplicateSuppressed: event.duplicateSuppressed || false
  };
  const msg = JSON.stringify(payload);
  for (const client of wsClients) {
    if (client.readyState === 1) client.send(msg);
  }
}

function getQueue() {
  return [...transactions.values()]
    .filter(t => ['QUEUED', 'FUNDING', 'MINTING'].includes(t.status))
    .sort((a, b) => a.queuePosition - b.queuePosition);
}

function loadScript() {
  try {
    const data = JSON.parse(readFileSync(SCRIPT_PATH, 'utf8'));
    for (const ev of data.events) {
      if (ev.type === 'DUPLICATE') {
        const t = transactions.get(ev.uetr);
        if (t) {
          t.status = 'DUPLICATE_SUPPRESSED';
          t.duplicateSuppressed = true;
          emitSettlementEvent({ ...t, eventType: 'DUPLICATE_SUPPRESSED', duplicateSuppressed: true });
        }
        continue;
      }
      if (ev.type === 'STATUS') {
        const t = transactions.get(ev.uetr);
        if (t) {
          t.status = ev.status;
          emitSettlementEvent(t);
        }
        continue;
      }
      const queueLen = getQueue().length;
      const tx = {
        uetr: ev.uetr,
        type: ev.type,
        status: ev.status,
        amount: ev.amount,
        participantId: ev.participantId,
        queuePosition: queueLen + 1,
        duplicateSuppressed: false,
        timeline: [{ status: ev.status, at: new Date().toISOString() }]
      };
      transactions.set(ev.uetr, tx);
      if (ev.status === 'DENIED') {
        emitSettlementEvent({ ...tx, eventType: 'QUEUE_UPDATE' });
      } else {
        emitSettlementEvent({ ...tx, eventType: 'QUEUE_UPDATE' });
      }
    }
    console.log(`Loaded scripted day: ${transactions.size} transactions, ${getQueue().length} pending`);
  } catch (e) {
    console.warn('Could not load scripted-day.json:', e.message, `(path=${SCRIPT_PATH})`);
  }
}

function resetDemo() {
  transactions.clear();
  limits.perIssuanceUsed = 0;
  limits.dailyCumulativeUsed = 1_200_000_000;
  loadScript();
}

// Minimal auth stub — decode bearer as base64 JSON for dev
function authMiddleware(req, res, next) {
  const auth = req.headers.authorization;
  if (!auth?.startsWith('Bearer ')) {
    req.user = { participantId: 'BANK-A', role: 'MAKER', tier: 'TIER_1', displayName: 'Bank A Pilot' };
    return next();
  }
  try {
    req.user = JSON.parse(Buffer.from(auth.slice(7), 'base64').toString());
    next();
  } catch {
    res.status(401).json({ error: 'invalid token' });
  }
}

app.get('/health', async (_req, res) => {
  const policyUp = await checkPolicyHealth();
  res.json({
    status: 'UP',
    service: 'portal-sim',
    policyUrl: process.env.POLICY_URL || 'http://localhost:8084',
    policyReachable: policyUp,
  });
});

app.get('/api/v1/auth/me', authMiddleware, (req, res) => {
  res.json(req.user);
});

app.post('/api/v1/issuance', authMiddleware, async (req, res) => {
  const { amount, valueDate, fundingReference } = req.body;
  if (!amount || !valueDate || !fundingReference) {
    return res.status(400).json({ error: 'validation failed' });
  }

  await refreshCapsFromPolicy();
  try {
    const decision = await evaluatePolicy({
      participantId: req.user.participantId,
      tier: req.user.tier,
      amount,
      dailyCumulative: limits.dailyCumulativeUsed,
    });
    if (decision.decision === 'DENY') {
      return res.status(403).json({ error: 'policy denied', reason: decision.reason });
    }
  } catch (e) {
    console.warn('Policy evaluate failed, falling back to local cap:', e.message);
    if (amount > limits.perIssuanceCap) {
      return res.status(403).json({ error: 'policy denied', reason: 'per_issuance_cap' });
    }
  }

  const uetr = randomUUID();
  const queuePosition = getQueue().length + 1;
  const tx = {
    uetr,
    type: 'ISSUANCE',
    status: 'QUEUED',
    amount,
    participantId: req.user.participantId,
    queuePosition,
    duplicateSuppressed: false,
    valueDate,
    fundingReference,
    timeline: [{ status: 'QUEUED', at: new Date().toISOString() }]
  };
  transactions.set(uetr, tx);
  limits.perIssuanceUsed = Math.min(amount, limits.perIssuanceCap);
  limits.dailyCumulativeUsed += amount;
  emitSettlementEvent({ ...tx, eventType: 'QUEUE_UPDATE' });
  res.status(201).json(tx);
});

app.get('/api/v1/queue', authMiddleware, (_req, res) => {
  res.json(getQueue());
});

app.get('/api/v1/activity', authMiddleware, (_req, res) => {
  const items = [...transactions.values()].sort((a, b) => a.queuePosition - b.queuePosition);
  res.json(items);
});

/** Reset FAFO demo data (scripted day). Safe to call before Act 1 portal demo. */
app.post('/api/v1/demo/reset', (_req, res) => {
  resetDemo();
  res.json({
    reset: true,
    pending: getQueue().length,
    total: transactions.size
  });
});

app.get('/api/v1/limits', authMiddleware, async (_req, res) => {
  await refreshCapsFromPolicy();
  res.json(limits);
});

app.get('/api/v1/transactions/:uetr', authMiddleware, (req, res) => {
  const tx = transactions.get(req.params.uetr);
  if (!tx) return res.status(404).json({ error: 'not found' });

  const pacs009 = `<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08">
  <FICdtTrf>
    <GrpHdr><MsgId>${tx.uetr}</MsgId><CreDtTm>${new Date().toISOString()}</CreDtTm></GrpHdr>
    <CdtTrfTxInf>
      <PmtId><UETR>${tx.uetr}</UETR><EndToEndId>${tx.fundingReference || 'FUND-REF'}</EndToEndId></PmtId>
      <IntrBkSttlmAmt Ccy="IDR">${tx.amount}</IntrBkSttlmAmt>
      <Dbtr><FinInstnId><BICFI>${tx.participantId}</BICFI></FinInstnId></Dbtr>
      <Cdtr><FinInstnId><BICFI>BI-OMNIBUS</BICFI></FinInstnId></Cdtr>
    </CdtTrfTxInf>
  </FICdtTrf>
</Document>`;

  res.json({
    ...tx,
    iso20022: { messageType: 'pacs.009.001.08', xml: pacs009 }
  });
});

app.post('/api/v1/replay/advance', (_req, res) => {
  resetDemo();
  res.json({ replayed: true, transactionCount: transactions.size, pending: getQueue().length });
});

wss.on('connection', (ws) => {
  wsClients.add(ws);
  ws.on('close', () => wsClients.delete(ws));
});

// Auto-replay scripted day on startup
setTimeout(async () => {
  resetDemo();
  await refreshCapsFromPolicy();
}, 1000);

server.listen(PORT, () => {
  console.log(`portal-sim listening on :${PORT}`);
});
