package id.bi.detp.policy.repository;

import id.bi.detp.policy.domain.PolicyRuleRecord;
import id.bi.detp.policy.domain.RuleStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PolicyRuleRepository {

    private static final RowMapper<PolicyRuleRecord> ROW_MAPPER = (rs, rowNum) -> new PolicyRuleRecord(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("drl_content"),
            rs.getInt("version"),
            RuleStatus.from(rs.getString("status")),
            rs.getTimestamp("effective_from").toInstant(),
            rs.getString("author_id"),
            rs.getString("approver_id"));

    private final JdbcTemplate jdbc;

    public PolicyRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PolicyRuleRecord> findAll(Optional<RuleStatus> status) {
        if (status.isPresent()) {
            return jdbc.query(
                    "SELECT * FROM detp.policy_rules WHERE status = ? ORDER BY name, version DESC",
                    ROW_MAPPER, status.get().name());
        }
        return jdbc.query("SELECT * FROM detp.policy_rules ORDER BY created_at DESC", ROW_MAPPER);
    }

    public List<PolicyRuleRecord> findActiveEffective(Instant asOf) {
        return jdbc.query("""
                SELECT DISTINCT ON (name) *
                FROM detp.policy_rules
                WHERE status = 'ACTIVE' AND effective_from <= ?
                ORDER BY name, version DESC
                """, ROW_MAPPER, Timestamp.from(asOf));
    }

    public Optional<PolicyRuleRecord> findById(UUID id) {
        var rows = jdbc.query("SELECT * FROM detp.policy_rules WHERE id = ?", ROW_MAPPER, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public PolicyRuleRecord insert(String name, String drlContent, String authorId, Instant effectiveFrom) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO detp.policy_rules (name, drl_content, author_id, effective_from)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, UUID.class, name, drlContent, authorId, Timestamp.from(effectiveFrom));
        return findById(id).orElseThrow();
    }

    public PolicyRuleRecord updateStatus(UUID id, RuleStatus status, String approverId) {
        if (approverId != null) {
            jdbc.update(
                    "UPDATE detp.policy_rules SET status = ?, approver_id = ?, updated_at = NOW() WHERE id = ?",
                    status.name(), approverId, id);
        } else {
            jdbc.update("UPDATE detp.policy_rules SET status = ?, updated_at = NOW() WHERE id = ?", status.name(), id);
        }
        return findById(id).orElseThrow();
    }

    public void insertAudit(UUID ruleId, String action, String actorId, String detailJson) {
        jdbc.update("""
                INSERT INTO detp.policy_audit (rule_id, action, actor_id, detail)
                VALUES (?, ?, ?, ?::jsonb)
                """, ruleId, action, actorId, detailJson);
    }

    public long countActiveByName(String name) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM detp.policy_rules WHERE name = ? AND status = 'ACTIVE'",
                Long.class, name);
        return count == null ? 0 : count;
    }

    public void deactivateByNameExcept(UUID keepId, String name) {
        jdbc.update(
                "UPDATE detp.policy_rules SET status = 'APPROVED', updated_at = NOW() WHERE name = ? AND id <> ? AND status = 'ACTIVE'",
                name, keepId);
    }

    public void publishOutbox(UUID ruleId, String payload) {
        jdbc.update("""
                INSERT INTO detp.outbox (aggregate_type, aggregate_id, event_type, payload)
                VALUES ('policy', ?, 'ConfigChanged', ?::jsonb)
                """, ruleId.toString(), payload);
    }
}
