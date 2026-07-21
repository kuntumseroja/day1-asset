package id.bi.detp.saga.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.SignalMethod;

@WorkflowInterface
public interface RedemptionSaga {
    @WorkflowMethod
    SagaResult run(IssuanceRequest request);

    @SignalMethod
    void burnConfirmed(String uetr);

    @SignalMethod
    void rtgsReleaseConfirmed(String uetr);
}
