package id.bi.detp.policy.api.dto;

public record EvaluationResult(
        String decision,
        String reason,
        String ruleId,
        int ruleVersion) {
}
