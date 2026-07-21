package id.bi.detp.firefly.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import id.bi.detp.firefly.listener.ConfirmationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class SagaBridge {

    private static final Logger log = LoggerFactory.getLogger(SagaBridge.class);

    private final ConfirmationListener listener;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${saga.url:http://localhost:8086}")
    private String sagaUrl;

    public SagaBridge(ConfirmationListener listener) {
        this.listener = listener;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        listener.onConfirmation(this::forwardToSaga);
        listener.connect();
        log.info("SagaBridge listening — forwarding finality events to {}", sagaUrl);
    }

    private void forwardToSaga(JsonNode event) {
        try {
            String type = event.path("type").asText();
            String idempotencyKey = event.path("idempotencyKey").asText();
            String eventId = event.path("eventId").asText();
            if (idempotencyKey.isBlank()) return;

            String endpoint = switch (type) {
                case "token_burn_confirmed" -> "/api/v1/dlt/finality-confirmed";
                default -> "/api/v1/dlt/finality-confirmed";
            };

            var body = String.format("{\"uetr\":\"%s\",\"eventId\":\"%s\"}", idempotencyKey, eventId);
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(sagaUrl + endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Forwarded {} for UETR {} → HTTP {}", type, idempotencyKey, resp.statusCode());
        } catch (Exception e) {
            log.warn("Failed to forward event to saga: {}", e.getMessage());
        }
    }
}
