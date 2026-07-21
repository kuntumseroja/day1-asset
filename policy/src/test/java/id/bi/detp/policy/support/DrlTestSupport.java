package id.bi.detp.policy.support;

import id.bi.detp.policy.domain.PolicyDecision;
import id.bi.detp.policy.domain.PolicyFact;
import id.bi.detp.policy.domain.PolicyRuleRecord;
import id.bi.detp.policy.domain.RuleStatus;
import id.bi.detp.policy.service.PolicyService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DrlTestSupport {

    private DrlTestSupport() {
    }

    public static String loadSample(String name) throws Exception {
        var resource = DrlTestSupport.class.getClassLoader().getResourceAsStream("samples/" + name + ".drl");
        if (resource != null) {
            return new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Path path = Path.of("samples", name + ".drl");
        if (!Files.exists(path)) {
            path = Path.of("src/main/resources/samples", name + ".drl");
        }
        return Files.readString(path);
    }

    public static PolicyDecision evaluateSample(PolicyService service, String sampleName, PolicyFact fact)
            throws Exception {
        String drl = loadSample(sampleName);
        PolicyRuleRecord rule = new PolicyRuleRecord(
                UUID.randomUUID(), sampleName, drl, 1, RuleStatus.ACTIVE, Instant.now(), "test", null);
        return service.evaluateWithRules(List.of(rule), fact);
    }

    public static PolicyFact fact(long amount, long dailyCumulative, String tier, Instant timestamp) {
        return new PolicyFact("BANK-A", tier, amount, dailyCumulative, timestamp);
    }
}
