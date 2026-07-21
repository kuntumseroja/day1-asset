package id.bi.detp.policy.api.dto;

import java.time.Instant;

public record PolicyRuleDto(
        String id,
        String name,
        String drlContent,
        int version,
        String status,
        Instant effectiveFrom,
        String authorId,
        String approverId) {
}
