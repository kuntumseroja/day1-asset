package id.bi.detp.policy.api.dto;

import java.time.Instant;

public record CreateRuleRequest(
        String name,
        String drlContent,
        String authorId,
        Instant effectiveFrom) {
}
