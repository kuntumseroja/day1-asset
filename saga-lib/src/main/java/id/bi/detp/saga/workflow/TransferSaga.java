package id.bi.detp.saga.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.SignalMethod;

@WorkflowInterface
public interface TransferSaga {
    @WorkflowMethod
    SagaResult run(IssuanceRequest request);

    @SignalMethod
    void rtgsDebitConfirmed(String uetr);

    @SignalMethod
    void dltFinalityConfirmed(String uetr, String eventId);
}
