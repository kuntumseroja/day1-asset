package id.bi.detp.policy.domain;

public enum RuleStatus {
    DRAFT,
    TESTED,
    APPROVED,
    ACTIVE;

    public static RuleStatus from(String value) {
        return RuleStatus.valueOf(value);
    }
}
