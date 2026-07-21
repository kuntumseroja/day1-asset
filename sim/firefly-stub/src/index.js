import express from 'express';
import { createServer } from 'http';
import { WebSocketServer } from 'ws';
import { randomUUID } from 'crypto';

const app = express();
const server = createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

app.use(express.json());

const PORT = process.env.PORT || 8082;

let totalSupply = 0n;
const mints = new Map();
let suppressNextConfirmation = false;
let disconnectWsClients = false;
let eventOffset = 0;
const eventLog = [];

const wsClients = new Set();

function broadcast(event) {
  eventOffset += 1;
  const payload = { ...event, offset: eventOffset, eventId: randomUUID(), finalized: true };
  eventLog.push(payload);

  if (suppressNextConfirmation) {
    suppressNextConfirmation = false;
    return payload;
  }

  const msg = JSON.stringify(payload);
  for (const client of wsClients) {
    if (client.readyState === 1) client.send(msg);
  }
  return payload;
}

app.get('/health', (_req, res) => {
  res.json({ status: 'UP', service: 'firefly-stub', totalSupply: totalSupply.toString() });
});

// FireFly mint API
app.post('/api/v1/tokens/mint', (req, res) => {
  const { pool, amount, idempotencyKey } = req.body;
  if (!pool || !amount || !idempotencyKey) {
    return res.status(400).json({ error: 'pool, amount, idempotencyKey required' });
  }

  if (mints.has(idempotencyKey)) {
    return res.status(200).json({
      status: 'DUPLICATE_ACCEPTED',
      idempotencyKey,
      mintId: mints.get(idempotencyKey).mintId
    });
  }

  if (amount === 'REJECT_ME') {
    return res.status(422).json({ error: 'DETERMINISTIC_REJECT', code: 'POLICY_REJECT' });
  }

  const mintId = randomUUID();
  mints.set(idempotencyKey, { mintId, amount: BigInt(amount), pool });

  // Simulate transient failure
  if (amount === 'TRANSIENT_FAIL') {
    return res.status(503).json({ error: 'TRANSIENT_RETRY', code: 'NODE_BUSY' });
  }

  totalSupply += BigInt(amount);

  const event = broadcast({
    type: 'token_mint_confirmed',
    pool,
    amount: amount.toString(),
    idempotencyKey,
    mintId,
    blockHeight: 1000 + mints.size,
    finality: 'QBFT_FINAL'
  });

  res.status(202).json({ status: 'ACCEPTED', mintId, idempotencyKey, eventId: event?.eventId });
});

app.post('/api/v1/tokens/burn', (req, res) => {
  const { pool, amount, idempotencyKey } = req.body;
  totalSupply -= BigInt(amount);
  broadcast({
    type: 'token_burn_confirmed',
    pool,
    amount: amount.toString(),
    idempotencyKey,
    blockHeight: 1000 + mints.size
  });
  res.status(202).json({ status: 'ACCEPTED', idempotencyKey });
});

app.get('/api/v1/tokens/wRD/supply', (req, res) => {
  const blockHeight = req.query.blockHeight || 'finalized';
  res.json({
    pool: 'wRD',
    totalSupply: totalSupply.toString(),
    blockHeight,
    finalized: blockHeight === 'finalized' || blockHeight === '1000'
  });
});

// Control endpoints for test scenarios
app.post('/control/suppress-next-confirmation', (_req, res) => {
  suppressNextConfirmation = true;
  res.json({ suppressed: true });
});

app.post('/control/disconnect-ws', (_req, res) => {
  for (const client of wsClients) client.close();
  wsClients.clear();
  res.json({ disconnected: true });
});

app.post('/control/replay-from-offset', (req, res) => {
  const fromOffset = req.body.offset || 0;
  const events = eventLog.filter(e => e.offset > fromOffset);
  res.json({ events, count: events.length });
});

app.get('/control/supply', (_req, res) => {
  res.json({ totalSupply: totalSupply.toString(), mintCount: mints.size });
});

app.post('/control/reset-supply', (req, res) => {
  totalSupply = BigInt(req.body.supply || 0);
  res.json({ totalSupply: totalSupply.toString() });
});

wss.on('connection', (ws, req) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const resumeFrom = parseInt(url.searchParams.get('offset') || '0', 10);

  wsClients.add(ws);

  // Replay missed events on reconnect
  const missed = eventLog.filter(e => e.offset > resumeFrom);
  for (const event of missed) {
    ws.send(JSON.stringify(event));
  }

  ws.on('close', () => wsClients.delete(ws));
});

server.listen(PORT, () => {
  console.log(`firefly-stub listening on :${PORT}`);
});

export { eventLog, broadcast, mints, totalSupply };
