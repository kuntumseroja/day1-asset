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

class CutoffWindowBoundaryTest {

    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(Clock.systemUTC());
    }

    @Test
    void insideBusinessHoursAllows() throws Exception {
        Instant morning = Instant.parse("2026-07-20T08:00:00Z");
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "cutoff_window",
                DrlTestSupport.fact(100_000_000L, 0L, "TIER_1", morning));
        assertEquals("ALLOW", decision.getDecision());
    }

    @Test
    void lateNightDenies() throws Exception {
        Instant late = Instant.parse("2026-07-20T23:30:00Z");
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "cutoff_window",
                DrlTestSupport.fact(100_000_000L, 0L, "TIER_1", late));
        assertEquals("DENY", decision.getDecision());
        assertEquals("cutoff_window", decision.getReason());
    }

    @Test
    void earlyMorningDenies() throws Exception {
        Instant early = Instant.parse("2026-07-20T05:59:00Z");
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "cutoff_window",
                DrlTestSupport.fact(100_000_000L, 0L, "TIER_1", early));
        assertEquals("DENY", decision.getDecision());
    }
}
