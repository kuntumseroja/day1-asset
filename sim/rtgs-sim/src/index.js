import express from 'express';
import { randomUUID } from 'crypto';

const app = express();
app.use(express.json({ limit: '1mb' }));

const PORT = process.env.PORT || 8081;
const SAGA_WEBHOOK_URL = process.env.SAGA_WEBHOOK_URL || 'http://localhost:8086/api/v1/rtgs/debit-confirmed';

const debits = new Map();
let omnibusBalance = 10_000_000_000_000n; // 10T Rp starting omnibus

app.get('/health', (_req, res) => {
  res.json({ status: 'UP', service: 'rtgs-sim' });
});

// Accept pacs.009 (simplified JSON representation)
app.post('/api/v1/pacs009', async (req, res) => {
  const { uetr, amount, debtorAgent, creditorAgent } = req.body;
  if (!uetr || !amount) {
    return res.status(400).json({ error: 'uetr and amount required' });
  }

  if (debits.has(uetr)) {
    return res.status(200).json({ status: 'DUPLICATE', uetr, original: debits.get(uetr) });
  }

  const debit = {
    uetr,
    amount: BigInt(amount),
    debtorAgent: debtorAgent || 'BANK-A',
    creditorAgent: creditorAgent || 'BI-OMNIBUS',
    confirmedAt: new Date().toISOString(),
    messageId: randomUUID()
  };

  debits.set(uetr, debit);
  omnibusBalance += debit.amount;

  // Emit debit-confirmed webhook to saga (async, best-effort)
  fetch(SAGA_WEBHOOK_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ event: 'RtgsDebitConfirmed', uetr, amount: amount.toString(), confirmedAt: debit.confirmedAt })
  }).catch(() => {});

  res.status(202).json({ status: 'ACCEPTED', uetr, messageId: debit.messageId });
});

// camt.053 closing balance (simplified)
app.get('/api/v1/camt053', (_req, res) => {
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08">
  <BkToCstmrStmt>
    <Stmt>
      <Id>STMT-${Date.now()}</Id>
      <Bal>
        <Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>
        <Amt Ccy="IDR">${omnibusBalance.toString()}</Amt>
      </Bal>
    </Stmt>
  </BkToCstmrStmt>
</Document>`;

  res.json({
    messageType: 'camt.053.001.08',
    closingBalance: omnibusBalance.toString(),
    xml
  });
});

app.get('/api/v1/debits/:uetr', (req, res) => {
  const debit = debits.get(req.params.uetr);
  if (!debit) return res.status(404).json({ error: 'not found' });
  res.json({ ...debit, amount: debit.amount.toString() });
});

app.listen(PORT, () => {
  console.log(`rtgs-sim listening on :${PORT}`);
});
