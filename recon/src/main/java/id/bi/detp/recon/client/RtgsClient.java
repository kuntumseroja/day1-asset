package id.bi.detp.recon.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prowidesoftware.swift.model.mx.MxCamt05300108;
import id.bi.detp.recon.config.ReconProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class RtgsClient {

    private final ReconProperties properties;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public RtgsClient(ReconProperties properties) {
        this.properties = properties;
    }

    public long fetchClosingBalance() {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(properties.rtgsUrl() + "/api/v1/camt053"))
                    .GET()
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("rtgs-sim camt.053 failed: HTTP " + resp.statusCode());
            }
            JsonNode body = mapper.readTree(resp.body());
            String xml = body.get("xml").asText();
            try {
                return parseCamt053ClosingBalance(xml);
            } catch (Exception parseEx) {
                return Long.parseLong(body.get("closingBalance").asText());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch camt.053 from rtgs-sim", e);
        }
    }

    long parseCamt053ClosingBalance(String xml) {
        MxCamt05300108 mx = MxCamt05300108.parse(xml);
        var stmt = mx.getBkToCstmrStmt().getStmt().getFirst();
        for (var bal : stmt.getBal()) {
            if (bal.getTp() != null
                    && bal.getTp().getCdOrPrtry() != null
                    && "CLBD".equals(bal.getTp().getCdOrPrtry().getCd())) {
                BigDecimal amt = bal.getAmt().getValue();
                return amt.longValueExact();
            }
        }
        throw new IllegalStateException("CLBD closing balance not found in camt.053");
    }
}
