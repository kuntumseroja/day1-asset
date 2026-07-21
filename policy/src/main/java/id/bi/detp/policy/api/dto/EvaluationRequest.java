package id.bi.detp.policy.api.dto;

import java.time.Instant;

public record EvaluationRequest(
        String participantId,
        String tier,
        long amount,
        long dailyCumulative,
        Instant timestamp) {
}
