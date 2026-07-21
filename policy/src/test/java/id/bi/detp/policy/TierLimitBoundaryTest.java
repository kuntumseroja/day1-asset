package id.bi.detp.policy;

import id.bi.detp.policy.domain.PolicyDecision;
import id.bi.detp.policy.service.PolicyService;
import id.bi.detp.policy.support.DrlTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierLimitBoundaryTest {

    private PolicyService policyService;
    private static final Instant NOON = Instant.parse("2026-07-20T12:00:00Z");

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(Clock.fixed(NOON, ZoneOffset.UTC));
    }

    @Test
    void tier2BelowLimitAllows() throws Exception {
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "tier_limit",
                DrlTestSupport.fact(300_000_000L, 0L, "TIER_2", NOON));
        assertEquals("ALLOW", decision.getDecision());
    }

    @Test
    void tier2AtLimitAllows() throws Exception {
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "tier_limit",
                DrlTestSupport.fact(1_000_000_000L, 0L, "TIER_2", NOON));
        assertEquals("ALLOW", decision.getDecision());
    }

    @Test
    void tier2AboveLimitDenies() throws Exception {
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "tier_limit",
                DrlTestSupport.fact(1_000_000_001L, 0L, "TIER_2", NOON));
        assertEquals("DENY", decision.getDecision());
        assertEquals("tier_limit", decision.getReason());
    }
}
