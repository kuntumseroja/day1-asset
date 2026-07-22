import { FormEvent, useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Button,
  Form,
  FormGroup,
  InlineNotification,
  Stack,
  TextInput,
  Tile,
} from '@carbon/react';
import { api } from '../api/client';
import type { IssuanceRequest, LimitsDashboard } from '../api/types';
import { formatRp } from '../utils/format';

type FieldKey = 'amount' | 'valueDate' | 'fundingReference';
type FieldErrors = Partial<Record<FieldKey, string>>;

function validateField(key: FieldKey, req: IssuanceRequest, cap: number): string | undefined {
  switch (key) {
    case 'amount': {
      if (!req.amount || req.amount < 1) return 'Amount must be at least Rp 1';
      if (req.amount > cap) return `Amount exceeds per-issuance cap (${formatRp(cap)})`;
      return undefined;
    }
    case 'valueDate':
      return req.valueDate ? undefined : 'Value date is required';
    case 'fundingReference':
      if (!req.fundingReference) return 'Funding reference is required';
      if (req.fundingReference.length > 64) return 'Funding reference must be 64 characters or fewer';
      return undefined;
  }
}

function validateAll(req: IssuanceRequest, cap: number): FieldErrors {
  const errors: FieldErrors = {};
  for (const key of ['amount', 'valueDate', 'fundingReference'] as FieldKey[]) {
    const message = validateField(key, req, cap);
    if (message) errors[key] = message;
  }
  return errors;
}

function firstError(errors: FieldErrors): string | null {
  return errors.amount ?? errors.valueDate ?? errors.fundingReference ?? null;
}

// KF: BC-01.02 — Issuance request form (amount Rp, value date, funding ref)
export function IssuanceScreen() {
  const navigate = useNavigate();
  const [amount, setAmount] = useState('500000000');
  const [valueDate, setValueDate] = useState(new Date().toISOString().slice(0, 10));
  const [fundingReference, setFundingReference] = useState('FUND-2026-001');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [limits, setLimits] = useState<LimitsDashboard | null>(null);
  const [limitsError, setLimitsError] = useState(false);

  const perIssuanceCap = limits?.perIssuanceCap ?? 500_000_000;
  const remainingDaily =
    limits != null ? limits.dailyCumulativeCap - limits.dailyCumulativeUsed : null;

  useEffect(() => {
    void api
      .getLimits()
      .then((data) => {
        setLimits(data);
        setLimitsError(false);
      })
      .catch(() => setLimitsError(true));
  }, []);

  const buildRequest = useCallback(
    (): IssuanceRequest => ({
      amount: Number(amount),
      valueDate,
      fundingReference,
    }),
    [amount, valueDate, fundingReference],
  );

  const validateOnBlur = (key: FieldKey) => {
    const message = validateField(key, buildRequest(), perIssuanceCap);
    setFieldErrors((prev) => {
      const next = { ...prev };
      if (message) next[key] = message;
      else delete next[key];
      return next;
    });
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const req = buildRequest();
    const errors = validateAll(req, perIssuanceCap);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setFormError(firstError(errors));
      return;
    }

    setSubmitting(true);
    setFormError(null);
    setFieldErrors({});
    try {
      const tx = await api.submitIssuance(req);
      navigate(`/transactions/${tx.uetr}`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Submission failed');
    } finally {
      setSubmitting(false);
    }
  };

  const fundingLength = fundingReference.length;

  return (
    <Tile>
      <Stack gap={6}>
        <header>
          <h2 id="issuance-heading">Issuance Request</h2>
          <p style={{ marginTop: '0.5rem', maxWidth: '42rem' }}>
            Submit a new token issuance for FAFO settlement. Amounts are validated against live
            policy caps before queueing.
          </p>
        </header>

        {limitsError && (
          <InlineNotification
            kind="warning"
            title="Limits unavailable"
            subtitle="Using default cap (Rp 500.000.000). Check the Limits screen for current policy."
            hideCloseButton
            lowContrast
          />
        )}

        {limits && (
          <aside
            aria-label="Current limit snapshot"
            style={{
              padding: '1rem',
              borderLeft: '3px solid var(--cds-border-interactive)',
              background: 'var(--cds-layer-01)',
            }}
          >
            <p style={{ margin: 0, fontSize: '0.875rem' }}>
              Per-issuance cap: <strong>{formatRp(limits.perIssuanceCap)}</strong>
              {' · '}
              Daily remaining:{' '}
              <strong>{formatRp(Math.max(0, limits.dailyCumulativeCap - limits.dailyCumulativeUsed))}</strong>
              {' · '}
              <Link to="/limits" className="cds--link">
                View limits dashboard
              </Link>
            </p>
          </aside>
        )}

        <Form aria-labelledby="issuance-heading" onSubmit={handleSubmit}>
          <Stack gap={6}>
            <FormGroup legendText="Transaction details">
              <Stack gap={5}>
                <TextInput
                  id="amount"
                  labelText="Amount (Rp)"
                  type="number"
                  min={1}
                  max={perIssuanceCap}
                  value={amount}
                  onChange={(e) => {
                    setAmount(e.target.value);
                    if (fieldErrors.amount) {
                      setFieldErrors((prev) => {
                        const next = { ...prev };
                        delete next.amount;
                        return next;
                      });
                    }
                  }}
                  onBlur={() => validateOnBlur('amount')}
                  helperText={`Whole Rupiah units · max ${formatRp(perIssuanceCap)}${
                    remainingDaily != null ? ` · ${formatRp(remainingDaily)} daily headroom` : ''
                  }`}
                  invalid={!!fieldErrors.amount}
                  invalidText={fieldErrors.amount}
                  required
                />
                <TextInput
                  id="valueDate"
                  labelText="Value date"
                  type="date"
                  value={valueDate}
                  onChange={(e) => {
                    setValueDate(e.target.value);
                    if (fieldErrors.valueDate) {
                      setFieldErrors((prev) => {
                        const next = { ...prev };
                        delete next.valueDate;
                        return next;
                      });
                    }
                  }}
                  onBlur={() => validateOnBlur('valueDate')}
                  helperText="Settlement value date (ISO 8601)"
                  invalid={!!fieldErrors.valueDate}
                  invalidText={fieldErrors.valueDate}
                  required
                />
              </Stack>
            </FormGroup>

            <FormGroup legendText="Funding">
              <TextInput
                id="fundingReference"
                labelText="Funding reference"
                value={fundingReference}
                onChange={(e) => {
                  setFundingReference(e.target.value);
                  if (fieldErrors.fundingReference) {
                    setFieldErrors((prev) => {
                      const next = { ...prev };
                      delete next.fundingReference;
                      return next;
                    });
                  }
                }}
                onBlur={() => validateOnBlur('fundingReference')}
                maxLength={64}
                helperText={`Bank funding reference · ${fundingLength}/64 characters`}
                invalid={!!fieldErrors.fundingReference}
                invalidText={fieldErrors.fundingReference}
                required
              />
            </FormGroup>

            {formError && (
              <InlineNotification
                kind="error"
                title="Unable to submit"
                subtitle={formError}
                hideCloseButton
                role="alert"
              />
            )}

            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
              <Button type="submit" disabled={submitting}>
                {submitting ? 'Submitting…' : 'Submit issuance'}
              </Button>
              <Link to="/queue" className="cds--link">
                View FAFO queue
              </Link>
            </div>
          </Stack>
        </Form>
      </Stack>
    </Tile>
  );
}
