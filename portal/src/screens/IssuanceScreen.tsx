import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Form, InlineNotification, Stack, TextInput, Tile } from '@carbon/react';
import { api } from '../api/client';
import type { IssuanceRequest } from '../api/types';

const PER_ISSUANCE_CAP = 500_000_000;

function validate(req: IssuanceRequest): string | null {
  if (!req.amount || req.amount < 1) return 'Amount must be at least Rp 1';
  if (req.amount > PER_ISSUANCE_CAP) return `Amount exceeds per-issuance cap (Rp ${PER_ISSUANCE_CAP.toLocaleString('id-ID')})`;
  if (!req.valueDate) return 'Value date is required';
  if (!req.fundingReference || req.fundingReference.length > 64) {
    return 'Funding reference is required (max 64 chars)';
  }
  return null;
}

// KF: BC-01.02 — Issuance request form (amount Rp, value date, funding ref)
export function IssuanceScreen() {
  const navigate = useNavigate();
  const [amount, setAmount] = useState('500000000');
  const [valueDate, setValueDate] = useState(new Date().toISOString().slice(0, 10));
  const [fundingReference, setFundingReference] = useState('FUND-2026-001');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const req: IssuanceRequest = {
      amount: Number(amount),
      valueDate,
      fundingReference,
    };
    const validationError = validate(req);
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const tx = await api.submitIssuance(req);
      navigate(`/transactions/${tx.uetr}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Submission failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Tile>
      <Stack gap={6}>
        <h2>Issuance Request</h2>
        <Form onSubmit={handleSubmit}>
          <Stack gap={5}>
            <TextInput
              id="amount"
              labelText="Amount (Rp)"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              helperText="Whole Rupiah units"
            />
            <TextInput
              id="valueDate"
              labelText="Value date"
              type="date"
              value={valueDate}
              onChange={(e) => setValueDate(e.target.value)}
            />
            <TextInput
              id="fundingReference"
              labelText="Funding reference"
              value={fundingReference}
              onChange={(e) => setFundingReference(e.target.value)}
              maxLength={64}
            />
            {error && (
              <InlineNotification kind="error" title="Error" subtitle={error} hideCloseButton />
            )}
            <Button type="submit" disabled={submitting}>
              Submit issuance
            </Button>
          </Stack>
        </Form>
      </Stack>
    </Tile>
  );
}
