# Production Deltas — FireFly Integration Kit

Differences between dev stub and Kaleido-managed FireFly on Besu QBFT production.

## Transport & Identity

| Dev | Production |
|-----|------------|
| Plain HTTP/WS to firefly-stub | mTLS with client certificates |
| No operator identity | FireFly operator identity per node (BR-011) |

## Finality semantics (BR-012)

- **Dev:** stub marks all confirmations `finalized: true` immediately
- **Production:** `ConfirmationListener` must wait for QBFT finality (≥2/3 validators), not mere receipt
- Receipt-only events are logged and discarded

## Multi-node QBFT (BR-011)

- 4-validator Besu network; finality threshold configurable
- Event ordering may differ across nodes; listener dedup by `eventId` + block hash

## Back-pressure

- Production: bounded queue for WS events; shed load with metrics alert before OOM
- Retry with exponential backoff capped at 30s for TRANSIENT errors only

## Signing identity

- Dev: stub accepts any mint request
- Production: mint/burn signed with HSM key from ceremony (Asset 5, KC-01)

## Pact contract portability

- Consumer contract published to pact-broker; Kaleido runs provider verification in their CI
- Provider verification file: `firefly-kit/src/test/resources/pact/provider-verification.properties`
