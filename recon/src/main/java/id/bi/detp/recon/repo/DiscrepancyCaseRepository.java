package id.bi.detp.recon.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.bi.detp.recon.api.DiscrepancyCase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DiscrepancyCaseRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public DiscrepancyCaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insertCase(long delta, long rtgs, long platform, long chain, List<String> candidateUetrs) {
        UUID id = UUID.randomUUID();
        Instant sla = Instant.now().plus(24, ChronoUnit.HOURS);
        String uetrsJson;
        try {
            uetrsJson = mapper.writeValueAsString(candidateUetrs);
        } catch (Exception e) {
            uetrsJson = "[]";
        }
        jdbc.update("""
                INSERT INTO detp.discrepancy_cases
                    (id, status, delta, rtgs_balance, platform_balance, chain_balance, candidate_uetrs, sla_deadline)
                VALUES (?, 'OPEN', ?, ?, ?, ?, ?::jsonb, ?)
                """,
                id, delta, rtgs, platform, chain, uetrsJson, Timestamp.from(sla));
        return id;
    }

    public List<DiscrepancyCase> findCases(Optional<String> status) {
        String sql = """
                SELECT id, status, delta, rtgs_balance, platform_balance, chain_balance,
                       candidate_uetrs, sla_deadline, created_at
                FROM detp.discrepancy_cases
                """;
        if (status.isPresent()) {
            sql += " WHERE status = ? ORDER BY created_at DESC";
            return jdbc.query(sql, (rs, rowNum) -> mapRow(rs), status.get());
        }
        sql += " ORDER BY created_at DESC";
        return jdbc.query(sql, (rs, rowNum) -> mapRow(rs));
    }

    public int countOpenCases() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detp.discrepancy_cases WHERE status = 'OPEN'", Integer.class);
        return count != null ? count : 0;
    }

    public boolean resolve(UUID id, String resolution, String evidence) {
        int updated = jdbc.update("""
                UPDATE detp.discrepancy_cases
                SET status = 'RESOLVED', resolution = ?, evidence = ?, resolved_at = NOW()
                WHERE id = ? AND status = 'OPEN'
                """, resolution, evidence, id);
        return updated > 0;
    }

    private DiscrepancyCase mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<String> uetrs = parseUetrs(rs.getString("candidate_uetrs"));
        return new DiscrepancyCase(
                (UUID) rs.getObject("id"),
                rs.getString("status"),
                rs.getLong("delta"),
                rs.getLong("rtgs_balance"),
                rs.getLong("platform_balance"),
                rs.getLong("chain_balance"),
                uetrs,
                rs.getTimestamp("sla_deadline").toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private List<String> parseUetrs(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
