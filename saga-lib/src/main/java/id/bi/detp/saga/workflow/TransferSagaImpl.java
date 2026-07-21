package id.bi.detp.saga.workflow;

import id.bi.detp.saga.activity.SagaActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class TransferSagaImpl implements TransferSaga {

    private final SagaActivities activities = Workflow.newActivityStub(SagaActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(5)).build());

    private boolean rtgsConfirmed;
    private boolean dltFinalized;

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

        activities.submitRtgsFunding(request.uetr(), request.amount(), request.participantId());

        Workflow.await(Duration.ofMinutes(2), () -> rtgsConfirmed);
        if (!rtgsConfirmed) {
            activities.compensateRefund(request.uetr(), request.amount(), request.participantId());
            return new SagaResult(request.uetr(), "COMPENSATED", false);
        }

        try {
            activities.mintTokens(request.uetr(), request.amount());
        } catch (Exception e) {
            activities.compensateRefund(request.uetr(), request.amount(), request.participantId());
            return new SagaResult(request.uetr(), "COMPENSATED", false);
        }

        Workflow.await(Duration.ofMinutes(2), () -> dltFinalized);
        if (!dltFinalized) {
            activities.compensateRefund(request.uetr(), request.amount(), request.participantId());
            return new SagaResult(request.uetr(), "COMPENSATED", false);
        }

        activities.applyTransferState(request.uetr(), request.amount(), request.participantId(), "BANK-B");
        return new SagaResult(request.uetr(), "SETTLED", false);
    }

    @Override
    public void rtgsDebitConfirmed(String uetr) {
        rtgsConfirmed = true;
    }

    @Override
    public void dltFinalityConfirmed(String uetr, String eventId) {
        dltFinalized = true;
    }
}
