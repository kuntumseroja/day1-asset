package id.bi.detp.saga.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SagaActivities {
    @ActivityMethod
    SagaActivityResult checkIdempotency(String uetr);

    @ActivityMethod
    void mintTokens(String uetr, long amount);

    @ActivityMethod
    void burnTokens(String uetr, long amount);

    @ActivityMethod
    void applyStateAndSupply(String uetr, long amount, String participantId, String sagaType);

    @ActivityMethod
    void compensateRefund(String uetr, long amount, String participantId);
}

record SagaActivityResult(boolean duplicate, String originalStatus) {}
