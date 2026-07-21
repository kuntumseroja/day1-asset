package id.bi.detp.saga.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SagaActivities {
    @ActivityMethod
    SagaActivityResult checkIdempotency(String uetr);

    @ActivityMethod
    PolicyGateResult evaluatePolicy(String participantId, String tier, long amount, long dailyCumulative);

    @ActivityMethod
    void submitRtgsFunding(String uetr, long amount, String participantId);

    @ActivityMethod
    void mintTokens(String uetr, long amount);

    @ActivityMethod
    void burnTokens(String uetr, long amount);

    @ActivityMethod
    void applyIssuanceState(String uetr, long amount, String participantId);

    @ActivityMethod
    void applyRedemptionState(String uetr, long amount, String participantId);

    @ActivityMethod
    void applyTransferState(String uetr, long amount, String fromParticipant, String toParticipant);

    @ActivityMethod
    void compensateRefund(String uetr, long amount, String participantId);
}
