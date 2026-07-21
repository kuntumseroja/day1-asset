package id.bi.detp.policy;

import id.bi.detp.policy.domain.PolicyDecision;
import id.bi.detp.policy.domain.PolicyRuleRecord;
import id.bi.detp.policy.domain.RuleStatus;
import id.bi.detp.policy.service.PolicyService;
import id.bi.detp.policy.support.DrlTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YesterdayTrafficBoundaryTest {

    private PolicyService policyService;
    private static final Instant NOON = Instant.parse("2026-07-20T12:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        policyService = new PolicyService(Clock.fixed(NOON, ZoneOffset.UTC));
        List<PolicyRuleRecord> samples = List.of(
                rule("per_issuance_cap"),
                rule("daily_cumulative_cap"),
                rule("tier_limit"),
                rule("cutoff_window"));
        policyService.rebuild(samples);
    }

    @Test
    void fullRulesetMatchesReplayExpectations() throws Exception {
        assertDecision("ALLOW", 499_999_999L, 1_000_000_000L, "TIER_1", Instant.parse("2026-07-20T10:00:00Z"));
        assertDecision("ALLOW", 500_000_000L, 1_000_000_000L, "TIER_1", Instant.parse("2026-07-20T10:01:00Z"));
        assertDecision("DENY", 500_000_001L, 1_000_000_000L, "TIER_1", Instant.parse("2026-07-20T10:02:00Z"));
        assertDecision("ALLOW", 300_000_000L, 2_000_000_000L, "TIER_2", Instant.parse("2026-07-20T11:00:00Z"));
        assertDecision("DENY", 100_000_000L, 4_900_000_000L, "TIER_1", Instant.parse("2026-07-20T14:00:00Z"));
        assertDecision("DENY", 100_000_000L, 1_000_000_000L, "TIER_1", Instant.parse("2026-07-20T23:30:00Z"));
        assertDecision("ALLOW", 100_000_000L, 1_000_000_000L, "TIER_1", Instant.parse("2026-07-20T08:00:00Z"));
    }

    private void assertDecision(String expected, long amount, long daily, String tier, Instant ts) {
        PolicyDecision decision = policyService.evaluate(DrlTestSupport.fact(amount, daily, tier, ts));
        assertEquals(expected, decision.getDecision(), "amount=" + amount + " daily=" + daily + " tier=" + tier + " ts=" + ts);
        if ("DENY".equals(expected)) {
            assertTrue(decision.getReason() != null && !decision.getReason().isBlank());
        }
    }

    private PolicyRuleRecord rule(String name) throws Exception {
        return new PolicyRuleRecord(
                UUID.randomUUID(), name, DrlTestSupport.loadSample(name), 1,
                RuleStatus.ACTIVE, NOON, "test", null);
    }
}
