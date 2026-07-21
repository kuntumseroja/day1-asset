package id.bi.detp.saga.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Component
public class SagaActivitiesImpl implements SagaActivities {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String fireflyUrl = System.getenv().getOrDefault("FIREFLY_URL", "http://localhost:8092");
    private final String rtgsUrl = System.getenv().getOrDefault("RTGS_URL", "http://localhost:8091");

    public SagaActivitiesImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SagaActivityResult checkIdempotency(String uetr) {
        var rows = jdbc.queryForList(
                "SELECT original_response FROM detp.idempotency_registry WHERE uetr = ? AND expires_at > NOW()",
                uetr);
        if (!rows.isEmpty()) {
            var status = rows.get(0).get("original_response").toString();
            return new SagaActivityResult(true, status);
        }
        return new SagaActivityResult(false, null);
    }

    @Override
    public void mintTokens(String uetr, long amount) {
        post(fireflyUrl + "/api/v1/tokens/mint", Map.of("pool", "wRD", "amount", String.valueOf(amount), "idempotencyKey", uetr));
    }

    @Override
    public void burnTokens(String uetr, long amount) {
        post(fireflyUrl + "/api/v1/tokens/burn", Map.of("pool", "wRD", "amount", String.valueOf(amount), "idempotencyKey", uetr));
    }

    @Override
    public void applyStateAndSupply(String uetr, long amount, String participantId, String sagaType) {
        jdbc.execute((java.sql.Connection conn) -> {
            conn.setAutoCommit(false);
            try (var ps = conn.prepareStatement(
                    "INSERT INTO detp.saga_instances (uetr, saga_type, status, amount, participant_id) VALUES (?,?,?,?,?) ON CONFLICT (uetr) DO UPDATE SET status=?, updated_at=NOW()")) {
                ps.setString(1, uetr);
                ps.setString(2, sagaType);
                ps.setString(3, "SETTLED");
                ps.setLong(4, amount);
                ps.setString(5, participantId);
                ps.setString(6, "SETTLED");
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement(
                    "INSERT INTO detp.wallet_balances (participant_id, balance) VALUES (?,?) ON CONFLICT (participant_id) DO UPDATE SET balance = detp.wallet_balances.balance + ?, updated_at=NOW()")) {
                ps.setString(1, participantId);
                ps.setLong(2, amount);
                ps.setLong(3, amount);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("INSERT INTO detp.supply_ledger (total_supply) SELECT COALESCE((SELECT total_supply FROM detp.supply_ledger ORDER BY id DESC LIMIT 1),0) + ?")) {
                ps.setLong(1, amount);
                ps.executeUpdate();
            }
            var payload = mapper.writeValueAsString(Map.of("uetr", uetr, "status", "SETTLEMENT_COMPLETED"));
            try (var ps = conn.prepareStatement(
                    "INSERT INTO detp.outbox (aggregate_type, aggregate_id, event_type, payload) VALUES (?,?,?,?::jsonb)")) {
                ps.setString(1, "saga");
                ps.setString(2, uetr);
                ps.setString(3, "SettlementCompleted");
                ps.setString(4, payload);
                ps.executeUpdate();
            }
            var response = mapper.writeValueAsString(Map.of("uetr", uetr, "status", "SETTLED"));
            try (var ps = conn.prepareStatement(
                    "INSERT INTO detp.idempotency_registry (uetr, original_response, expires_at) VALUES (?,?,?) ON CONFLICT DO NOTHING")) {
                ps.setString(1, uetr);
                ps.setString(2, response);
                ps.setObject(3, Instant.now().plus(48, ChronoUnit.HOURS));
                ps.executeUpdate();
            }
            conn.commit();
            return null;
        });
    }

    @Override
    public void compensateRefund(String uetr, long amount, String participantId) {
        jdbc.update("UPDATE detp.saga_instances SET status = 'COMPENSATED', updated_at = NOW() WHERE uetr = ?", uetr);
        var payload = mapper.writeValueAsString(Map.of("uetr", uetr, "amount", amount, "participantId", participantId));
        jdbc.update("INSERT INTO detp.outbox (aggregate_type, aggregate_id, event_type, payload) VALUES (?,?,?,?::jsonb)",
                "saga", uetr, "CompensatingRefundInstruction", payload);
    }

    private void post(String url, Map<String, String> body) {
        try {
            var json = mapper.writeValueAsString(body);
            var req = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
