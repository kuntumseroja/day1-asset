package id.bi.detp.saga.api;

import id.bi.detp.saga.workflow.*;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
public class IntakeController {

    private final WorkflowClient workflowClient;

    public IntakeController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/intake/pacs009")
    public ResponseEntity<?> intakeIssuance(@RequestBody Map<String, Object> body) throws Exception {
        return startWorkflow(IssuanceSaga.class, body);
    }

    @PostMapping("/intake/redemption")
    public ResponseEntity<?> intakeRedemption(@RequestBody Map<String, Object> body) throws Exception {
        return startWorkflow(RedemptionSaga.class, body);
    }

    @PostMapping("/intake/transfer")
    public ResponseEntity<?> intakeTransfer(@RequestBody Map<String, Object> body) throws Exception {
        return startWorkflow(TransferSaga.class, body);
    }

    private ResponseEntity<?> startWorkflow(Class<?> workflowClass, Map<String, Object> body) throws Exception {
        String uetr = (String) body.get("uetr");
        long amount = Long.parseLong(body.get("amount").toString());
        String participantId = (String) body.getOrDefault("participantId", "BANK-A");
        var request = new IssuanceRequest(uetr, amount, participantId);

        if (workflowClass == IssuanceSaga.class) {
            IssuanceSaga workflow = workflowClient.newWorkflowStub(IssuanceSaga.class,
                    WorkflowOptions.newBuilder().setTaskQueue("saga-task-queue").setWorkflowId(uetr).build());
            SagaResult result = WorkflowClient.execute(workflow::run, request).get(3, TimeUnit.MINUTES);
            return ResponseEntity.accepted().body(result);
        }
        if (workflowClass == RedemptionSaga.class) {
            RedemptionSaga workflow = workflowClient.newWorkflowStub(RedemptionSaga.class,
                    WorkflowOptions.newBuilder().setTaskQueue("saga-task-queue").setWorkflowId("redemption-" + uetr).build());
            SagaResult result = WorkflowClient.execute(workflow::run, request).get(3, TimeUnit.MINUTES);
            return ResponseEntity.accepted().body(result);
        }
        TransferSaga workflow = workflowClient.newWorkflowStub(TransferSaga.class,
                WorkflowOptions.newBuilder().setTaskQueue("saga-task-queue").setWorkflowId("transfer-" + uetr).build());
        SagaResult result = WorkflowClient.execute(workflow::run, request).get(3, TimeUnit.MINUTES);
        return ResponseEntity.accepted().body(result);
    }

    @PostMapping("/rtgs/debit-confirmed")
    public ResponseEntity<?> rtgsConfirmed(@RequestBody Map<String, String> body) {
        String uetr = body.get("uetr");
        signalIssuance(uetr);
        signalTransfer(uetr);
        return ResponseEntity.ok(Map.of("signaled", true, "uetr", uetr));
    }

    @PostMapping("/rtgs/release-confirmed")
    public ResponseEntity<?> rtgsRelease(@RequestBody Map<String, String> body) {
        String uetr = body.get("uetr");
        RedemptionSaga workflow = workflowClient.newWorkflowStub(RedemptionSaga.class, "redemption-" + uetr);
        workflow.rtgsReleaseConfirmed(uetr);
        return ResponseEntity.ok(Map.of("signaled", true));
    }

    @PostMapping("/dlt/finality-confirmed")
    public ResponseEntity<?> dltFinality(@RequestBody Map<String, String> body) {
        String uetr = body.get("uetr");
        String eventId = body.getOrDefault("eventId", "");
        signalIssuanceFinality(uetr, eventId);
        signalTransferFinality(uetr, eventId);
        RedemptionSaga redemption = workflowClient.newWorkflowStub(RedemptionSaga.class, "redemption-" + uetr);
        redemption.burnConfirmed(uetr);
        return ResponseEntity.ok(Map.of("signaled", true, "uetr", uetr));
    }

    private void signalIssuance(String uetr) {
        try {
            IssuanceSaga workflow = workflowClient.newWorkflowStub(IssuanceSaga.class, uetr);
            workflow.rtgsDebitConfirmed(uetr);
        } catch (Exception ignored) {}
    }

    private void signalTransfer(String uetr) {
        try {
            TransferSaga workflow = workflowClient.newWorkflowStub(TransferSaga.class, "transfer-" + uetr);
            workflow.rtgsDebitConfirmed(uetr);
        } catch (Exception ignored) {}
    }

    private void signalIssuanceFinality(String uetr, String eventId) {
        try {
            IssuanceSaga workflow = workflowClient.newWorkflowStub(IssuanceSaga.class, uetr);
            workflow.dltFinalityConfirmed(uetr, eventId);
        } catch (Exception ignored) {}
    }

    private void signalTransferFinality(String uetr, String eventId) {
        try {
            TransferSaga workflow = workflowClient.newWorkflowStub(TransferSaga.class, "transfer-" + uetr);
            workflow.dltFinalityConfirmed(uetr, eventId);
        } catch (Exception ignored) {}
    }
}
