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

class PerIssuanceCapBoundaryTest {

    private PolicyService policyService;
    private static final Instant NOON = Instant.parse("2026-07-20T12:00:00Z");

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(Clock.fixed(NOON, ZoneOffset.UTC));
    }

    @Test
    void justBelowCapAllows() throws Exception {
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "per_issuance_cap", DrlTestSupport.fact(499_999_999L, 0L, "TIER_1", NOON));
        assertEquals("ALLOW", decision.getDecision());
    }

    @Test
    void atCapAllows() throws Exception {
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "per_issuance_cap", DrlTestSupport.fact(500_000_000L, 0L, "TIER_1", NOON));
        assertEquals("ALLOW", decision.getDecision());
    }

    @Test
    void aboveCapDenies() throws Exception {
        PolicyDecision decision = DrlTestSupport.evaluateSample(
                policyService, "per_issuance_cap", DrlTestSupport.fact(500_000_001L, 0L, "TIER_1", NOON));
        assertEquals("DENY", decision.getDecision());
        assertEquals("per_issuance_cap", decision.getReason());
    }
}
