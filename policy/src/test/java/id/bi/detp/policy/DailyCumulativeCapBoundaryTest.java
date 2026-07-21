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

class DailyCumulativeCapBoundaryTest {

    private PolicyService policyService;
    private static final Instant NOON = Instant.parse("2026-07-20T12:00:00Z");

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(Clock.fixed(NOON, ZoneOffset.UTC));
    }

    @Test
    void belowDailyCapAllows() throws Exception {
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "daily_cumulative_cap",
                DrlTestSupport.fact(100_000_000L, 4_800_000_000L, "TIER_1", NOON));
        assertEquals("ALLOW", decision.getDecision());
    }

    @Test
    void atDailyCapDenies() throws Exception {
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "daily_cumulative_cap",
                DrlTestSupport.fact(100_000_000L, 4_900_000_000L, "TIER_1", NOON));
        assertEquals("DENY", decision.getDecision());
        assertEquals("daily_cumulative_cap", decision.getReason());
    }

    @Test
    void aboveDailyCapDenies() throws Exception {
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "daily_cumulative_cap",
                DrlTestSupport.fact(200_000_000L, 4_900_000_000L, "TIER_1", NOON));
        assertEquals("DENY", decision.getDecision());
    }
}
