package id.bi.detp.saga.workflow;

import id.bi.detp.saga.activity.SagaActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class RedemptionSagaImpl implements RedemptionSaga {

    private final SagaActivities activities = Workflow.newActivityStub(SagaActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(5)).build());

    private boolean burnConfirmed;
    private boolean rtgsReleaseConfirmed;

    @Override
    public SagaResult run(IssuanceRequest request) {
        var idem = activities.checkIdempotency(request.uetr());
        if (idem.duplicate()) {
            return new SagaResult(request.uetr(), idem.originalStatus(), true);
        }

        var policy = activities.evaluatePolicy(request.participantId(), "TIER_1", request.amount(), 0);
        if (!policy.allowed()) {
            return new SagaResult(request.uetr(), "DENIED", false);
        }

        // Redemption: burn BEFORE release (mirror ordering)
        activities.burnTokens(request.uetr(), request.amount());

        Workflow.await(Duration.ofMinutes(2), () -> burnConfirmed);
        if (!burnConfirmed) {
            activities.compensateRefund(request.uetr(), request.amount(), request.participantId());
            return new SagaResult(request.uetr(), "COMPENSATED", false);
        }

        Workflow.await(Duration.ofMinutes(2), () -> rtgsReleaseConfirmed);
        if (!rtgsReleaseConfirmed) {
            activities.compensateRefund(request.uetr(), request.amount(), request.participantId());
            return new SagaResult(request.uetr(), "COMPENSATED", false);
        }

        activities.applyRedemptionState(request.uetr(), request.amount(), request.participantId());
        return new SagaResult(request.uetr(), "SETTLED", false);
    }

    @Override
    public void burnConfirmed(String uetr) {
        burnConfirmed = true;
    }

    @Override
    public void rtgsReleaseConfirmed(String uetr) {
        rtgsReleaseConfirmed = true;
    }
}
