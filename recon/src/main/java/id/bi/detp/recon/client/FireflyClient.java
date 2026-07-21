package id.bi.detp.recon.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.bi.detp.recon.config.ReconProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class FireflyClient {

    private final ReconProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public FireflyClient(ReconProperties properties) {
        this.properties = properties;
    }

    public ChainSupply fetchSupply() {
        String url = properties.fireflyUrl() + "/api/v1/tokens/wRD/supply";
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "?blockHeight=finalized"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException(
                        "firefly-stub supply failed: HTTP " + resp.statusCode() + " url=" + url + " body=" + resp.body());
            }
            JsonNode body = mapper.readTree(resp.body());
            long totalSupply = Long.parseLong(body.get("totalSupply").asText());
            long blockHeight = parseBlockHeight(body.get("blockHeight"));
            return new ChainSupply(totalSupply, blockHeight);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch chain supply from firefly-stub", e);
        }
    }

    private long parseBlockHeight(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0L;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        String text = node.asText();
        if ("finalized".equals(text)) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public record ChainSupply(long totalSupply, long blockHeight) {}
}
