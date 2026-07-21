package id.bi.detp.saga.activity;

import com.fasterxml.jackson.databind.JsonNode;
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
    private final String policyUrl = System.getenv().getOrDefault("POLICY_URL", "http://localhost:8084");

    public SagaActivitiesImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SagaActivityResult checkIdempotency(String uetr) {
        var rows = jdbc.queryForList(
                "SELECT original_response FROM detp.idempotency_registry WHERE uetr = ? AND expires_at > CURRENT_TIMESTAMP",
                uetr);
        if (!rows.isEmpty()) {
            var status = rows.get(0).get("original_response").toString();
            return new SagaActivityResult(true, status);
        }
        return new SagaActivityResult(false, null);
    }

    @Override
    public PolicyGateResult evaluatePolicy(String participantId, String tier, long amount, long dailyCumulative) {
        try {
            var body = mapper.writeValueAsString(Map.of(
                    "participantId", participantId,
                    "tier", tier,
                    "amount", amount,
                    "dailyCumulative", dailyCumulative,
                    "timestamp", Instant.now().toString()
            ));
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(policyUrl + "/api/v1/policy/evaluate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(resp.body());
            boolean allowed = "ALLOW".equals(json.path("decision").asText());
            return new PolicyGateResult(allowed, json.path("reason").asText(null));
        } catch (Exception e) {
            return new PolicyGateResult(false, "policy_unreachable");
        }
    }

    @Override
    public void submitRtgsFunding(String uetr, long amount, String participantId) {
        post(rtgsUrl + "/api/v1/pacs009", Map.of(
                "uetr", uetr,
                "amount", String.valueOf(amount),
                "debtorAgent", participantId
        ));
    }

    @Override
    public void mintTokens(String uetr, long amount) {
        post(fireflyUrl + "/api/v1/tokens/mint", Map.of(
                "pool", "wRD",
                "amount", String.valueOf(amount),
                "idempotencyKey", uetr
        ));
    }

    @Override
    public void burnTokens(String uetr, long amount) {
        post(fireflyUrl + "/api/v1/tokens/burn", Map.of(
                "pool", "wRD",
                "amount", String.valueOf(amount),
                "idempotencyKey", uetr
        ));
    }

    @Override
    public void applyIssuanceState(String uetr, long amount, String participantId) {
        applyState(uetr, amount, participantId, "ISSUANCE", amount, 0L);
    }

    @Override
    public void applyRedemptionState(String uetr, long amount, String participantId) {
        applyState(uetr, -amount, participantId, "REDEMPTION", -amount, 0L);
    }

    @Override
    public void applyTransferState(String uetr, long amount, String fromParticipant, String toParticipant) {
        jdbc.execute((java.sql.Connection conn) -> {
            conn.setAutoCommit(false);
            try {
                recordSaga(conn, uetr, "TRANSFER", "SETTLED", amount, fromParticipant);
                adjustWallet(conn, fromParticipant, -amount);
                adjustWallet(conn, toParticipant, amount);
                insertOutbox(conn, uetr, "SettlementCompleted", Map.of("uetr", uetr, "type", "TRANSFER"));
                recordIdempotency(conn, uetr, "SETTLED");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public void compensateRefund(String uetr, long amount, String participantId) {
        jdbc.update("UPDATE detp.saga_instances SET status = 'COMPENSATED', updated_at = NOW() WHERE uetr = ?", uetr);
        try {
            var payload = mapper.writeValueAsString(Map.of("uetr", uetr, "amount", amount, "participantId", participantId));
            jdbc.update("INSERT INTO detp.outbox (aggregate_type, aggregate_id, event_type, payload) VALUES (?,?,?,?)",
                    "saga", uetr, "CompensatingRefundInstruction", payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void applyState(String uetr, long walletDelta, String participantId, String sagaType,
                            long supplyDelta, long ignored) {
        jdbc.execute((java.sql.Connection conn) -> {
            conn.setAutoCommit(false);
            try {
                recordSaga(conn, uetr, sagaType, "SETTLED", Math.abs(walletDelta), participantId);
                adjustWallet(conn, participantId, walletDelta);
                if (supplyDelta != 0) {
                    try (var ps = conn.prepareStatement(
                            "INSERT INTO detp.supply_ledger (total_supply) SELECT COALESCE((SELECT total_supply FROM detp.supply_ledger ORDER BY id DESC LIMIT 1),0) + ?")) {
                        ps.setLong(1, supplyDelta);
                        ps.executeUpdate();
                    }
                }
                insertOutbox(conn, uetr, "SettlementCompleted", Map.of("uetr", uetr, "status", "SETTLED"));
                recordIdempotency(conn, uetr, "SETTLED");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private void recordSaga(java.sql.Connection conn, String uetr, String sagaType, String status,
                            long amount, String participantId) throws Exception {
        try (var ps = conn.prepareStatement(
                "UPDATE detp.saga_instances SET status=?, updated_at=CURRENT_TIMESTAMP WHERE uetr=?")) {
            ps.setString(1, status);
            ps.setString(2, uetr);
            if (ps.executeUpdate() == 0) {
                try (var ins = conn.prepareStatement(
                        "INSERT INTO detp.saga_instances (uetr, saga_type, status, amount, participant_id) VALUES (?,?,?,?,?)")) {
                    ins.setString(1, uetr);
                    ins.setString(2, sagaType);
                    ins.setString(3, status);
                    ins.setLong(4, amount);
                    ins.setString(5, participantId);
                    ins.executeUpdate();
                }
            }
        }
    }

    private void adjustWallet(java.sql.Connection conn, String participantId, long delta) throws Exception {
        try (var ps = conn.prepareStatement(
                "UPDATE detp.wallet_balances SET balance = balance + ?, updated_at=CURRENT_TIMESTAMP WHERE participant_id=?")) {
            ps.setLong(1, delta);
            ps.setString(2, participantId);
            if (ps.executeUpdate() == 0) {
                try (var ins = conn.prepareStatement(
                        "INSERT INTO detp.wallet_balances (participant_id, balance) VALUES (?,?)")) {
                    ins.setString(1, participantId);
                    ins.setLong(2, delta);
                    ins.executeUpdate();
                }
            }
        }
    }

    private void insertOutbox(java.sql.Connection conn, String uetr, String eventType, Map<String, Object> payload)
            throws Exception {
            try (var ps = conn.prepareStatement(
                    "INSERT INTO detp.outbox (aggregate_type, aggregate_id, event_type, payload) VALUES (?,?,?,?)")) {
                ps.setString(1, "saga");
                ps.setString(2, uetr);
                ps.setString(3, eventType);
                ps.setString(4, mapper.writeValueAsString(payload));
                ps.executeUpdate();
            }
    }

    private void recordIdempotency(java.sql.Connection conn, String uetr, String status) throws Exception {
        var response = mapper.writeValueAsString(Map.of("uetr", uetr, "status", status));
        try (var ps = conn.prepareStatement(
                "SELECT uetr FROM detp.idempotency_registry WHERE uetr = ?")) {
            ps.setString(1, uetr);
            var rs = ps.executeQuery();
            if (!rs.next()) {
                try (var ins = conn.prepareStatement(
                        "INSERT INTO detp.idempotency_registry (uetr, original_response, expires_at) VALUES (?,?,?)")) {
                    ins.setString(1, uetr);
                    ins.setString(2, response);
                    ins.setTimestamp(3, java.sql.Timestamp.from(Instant.now().plus(48, ChronoUnit.HOURS)));
                    ins.executeUpdate();
                }
            }
        }
    }

    private void post(String url, Map<String, String> body) {
        try {
            var json = mapper.writeValueAsString(body);
            var req = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
