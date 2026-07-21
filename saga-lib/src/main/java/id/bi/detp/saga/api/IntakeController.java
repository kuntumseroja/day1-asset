package id.bi.detp.saga.api;

import id.bi.detp.saga.workflow.IssuanceRequest;
import id.bi.detp.saga.workflow.IssuanceSaga;
import id.bi.detp.saga.workflow.SagaResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class IntakeController {

    private final WorkflowClient workflowClient;

    public IntakeController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/intake/pacs009")
    public ResponseEntity<?> intake(@RequestBody Map<String, Object> body) {
        String uetr = (String) body.get("uetr");
        long amount = Long.parseLong(body.get("amount").toString());
        String participantId = (String) body.getOrDefault("participantId", "BANK-A");

        IssuanceSaga workflow = workflowClient.newWorkflowStub(IssuanceSaga.class,
                WorkflowOptions.newBuilder().setTaskQueue("saga-task-queue").setWorkflowId(uetr).build());

        SagaResult result = WorkflowClient.execute(workflow::run, new IssuanceRequest(uetr, amount, participantId));
        return ResponseEntity.accepted().body(result);
    }

    @PostMapping("/rtgs/debit-confirmed")
    public ResponseEntity<?> rtgsConfirmed(@RequestBody Map<String, String> body) {
        String uetr = body.get("uetr");
        IssuanceSaga workflow = workflowClient.newWorkflowStub(IssuanceSaga.class, uetr);
        workflow.rtgsDebitConfirmed(uetr);
        return ResponseEntity.ok(Map.of("signaled", true));
    }

    @PostMapping("/dlt/finality-confirmed")
    public ResponseEntity<?> dltFinality(@RequestBody Map<String, String> body) {
        String uetr = body.get("uetr");
        IssuanceSaga workflow = workflowClient.newWorkflowStub(IssuanceSaga.class, uetr);
        workflow.dltFinalityConfirmed(uetr, body.get("eventId"));
        return ResponseEntity.ok(Map.of("signaled", true));
    }
}
