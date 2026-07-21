package id.bi.detp.firefly.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class FireflyMintClient {

    private static final Logger log = LoggerFactory.getLogger(FireflyMintClient.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public FireflyMintClient(@Value("${firefly.base-url:http://localhost:8092}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Visible for Pact/contract tests */
    public FireflyMintClient(String baseUrl, boolean forTest) {
        this.baseUrl = baseUrl;
    }

    public record MintResult(String status, String mintId, String idempotencyKey, MintErrorAction action) {}

    public MintResult mint(String pool, long amount, String idempotencyKey) {
        int attempts = 0;
        while (attempts < 4) {
            attempts++;
            try {
                var body = mapper.writeValueAsString(Map.of(
                        "pool", pool,
                        "amount", String.valueOf(amount),
                        "idempotencyKey", idempotencyKey
                ));
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/v1/tokens/mint"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                var action = mapResponse(response.statusCode(), response.body());
                if (action == MintErrorAction.TRANSIENT_RETRY) {
                    Thread.sleep((long) Math.pow(2, attempts) * 100);
                    continue;
                }
                JsonNode json = mapper.readTree(response.body());
                return new MintResult(
                        json.path("status").asText("ERROR"),
                        json.path("mintId").asText(null),
                        idempotencyKey,
                        action
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (Exception e) {
                log.warn("Mint attempt {} failed: {}", attempts, e.getMessage());
                if (attempts >= 4) throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Mint failed after retries");
    }

    MintErrorAction mapResponse(int status, String body) {
        if (status == 200 && body.contains("DUPLICATE_ACCEPTED")) return MintErrorAction.DUPLICATE_ACCEPTED;
        if (status == 422 || body.contains("DETERMINISTIC_REJECT")) return MintErrorAction.DETERMINISTIC_REJECT;
        if (status == 503 || body.contains("TRANSIENT_RETRY")) return MintErrorAction.TRANSIENT_RETRY;
        if (status >= 200 && status < 300) return MintErrorAction.DUPLICATE_ACCEPTED;
        return MintErrorAction.DETERMINISTIC_REJECT;
    }
}
