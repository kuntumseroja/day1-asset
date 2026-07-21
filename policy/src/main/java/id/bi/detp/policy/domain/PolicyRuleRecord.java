package id.bi.detp.policy.domain;

import java.time.Instant;
import java.util.UUID;

public record PolicyRuleRecord(
        UUID id,
        String name,
        String drlContent,
        int version,
        RuleStatus status,
        Instant effectiveFrom,
        String authorId,
        String approverId) {
}
