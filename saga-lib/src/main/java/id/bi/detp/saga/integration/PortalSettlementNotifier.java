package id.bi.detp.saga.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.bi.detp.saga.workflow.IssuanceRequest;
import id.bi.detp.saga.workflow.SagaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Component
public class PortalSettlementNotifier {

    private static final Logger log = LoggerFactory.getLogger(PortalSettlementNotifier.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final String portalSimUrl = System.getenv().getOrDefault("PORTAL_SIM_URL", "http://localhost:8093");

    public void notifyIssuanceResult(IssuanceRequest request, SagaResult result) {
        String status = result.duplicate() ? "DUPLICATE_SUPPRESSED" : result.status();
        String eventType = result.duplicate() ? "DUPLICATE_SUPPRESSED" : "STATUS_TRANSITION";
        notify(Map.of(
                "uetr", result.uetr(),
                "participantId", request.participantId(),
                "transactionType", "ISSUANCE",
                "status", status,
                "amount", request.amount(),
                "duplicateSuppressed", result.duplicate(),
                "eventType", eventType));
    }

    public void notify(String uetr, String participantId, String transactionType, String status, long amount,
            boolean duplicateSuppressed, String eventType) {
        notify(Map.of(
                "uetr", uetr,
                "participantId", participantId,
                "transactionType", transactionType,
                "status", status,
                "amount", amount,
                "duplicateSuppressed", duplicateSuppressed,
                "eventType", eventType));
    }

    private void notify(Map<String, Object> body) {
        try {
            var json = mapper.writeValueAsString(body);
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(portalSimUrl + "/api/v1/settlement/ingest"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                log.warn("Portal ingest HTTP {} for uetr {}", resp.statusCode(), body.get("uetr"));
            }
        } catch (Exception e) {
            log.debug("Portal ingest skipped for uetr {}: {}", body.get("uetr"), e.getMessage());
        }
    }
}
