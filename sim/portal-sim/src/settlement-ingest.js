/** Upsert saga/rtgs settlement events into the portal FAFO queue view. */
export function createSettlementIngest({ transactions, getQueue, emitSettlementEvent }) {
  function nextQueuePosition() {
    if (transactions.size === 0) return 1;
    return Math.max(...[...transactions.values()].map((t) => t.queuePosition)) + 1;
  }

  return function ingestSettlement(body) {
    const {
      uetr,
      participantId = 'BANK-A',
      transactionType = 'ISSUANCE',
      status,
      amount,
      duplicateSuppressed = false,
      eventType,
    } = body;

    if (!uetr || !status || amount == null) {
      throw new Error('uetr, status, and amount are required');
    }

    const now = new Date().toISOString();
    let tx = transactions.get(uetr);

    if (!tx) {
      tx = {
        uetr,
        type: transactionType,
        status,
        amount: Number(amount),
        participantId,
        queuePosition: nextQueuePosition(),
        duplicateSuppressed: Boolean(duplicateSuppressed),
        timeline: [{ status, at: now }],
      };
      transactions.set(uetr, tx);
    } else if (tx.duplicateSuppressed && status !== 'DUPLICATE_SUPPRESSED') {
      // Duplicate suppression is terminal — later saga retries must not overwrite it.
      return tx;
    } else {
      tx.status = status;
      tx.amount = Number(amount);
      tx.participantId = participantId;
      tx.type = transactionType;
      if (duplicateSuppressed) tx.duplicateSuppressed = true;
      tx.timeline.push({ status, at: now });
    }

    const resolvedEventType =
      eventType ||
      (tx.duplicateSuppressed || status === 'DUPLICATE_SUPPRESSED'
        ? 'DUPLICATE_SUPPRESSED'
        : 'STATUS_TRANSITION');

    emitSettlementEvent({
      ...tx,
      eventType: resolvedEventType,
      duplicateSuppressed: tx.duplicateSuppressed,
    });

    return tx;
  };
}
