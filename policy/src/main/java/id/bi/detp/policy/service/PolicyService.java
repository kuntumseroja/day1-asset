package id.bi.detp.policy.service;

import id.bi.detp.policy.domain.PolicyDecision;
import id.bi.detp.policy.domain.PolicyFact;
import id.bi.detp.policy.domain.PolicyRuleRecord;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PolicyService {

    private final AtomicReference<KieContainer> container = new AtomicReference<>();
    private final AtomicReference<Map<String, PolicyRuleRecord>> ruleIndex = new AtomicReference<>(Map.of());
    private final Clock clock;

    public PolicyService(Clock clock) {
        this.clock = clock;
    }

    public void rebuild(Collection<PolicyRuleRecord> activeRules) {
        var effective = activeRules.stream()
                .filter(r -> !r.effectiveFrom().isAfter(clock.instant()))
                .toList();
        if (effective.isEmpty()) {
            container.set(null);
            ruleIndex.set(Map.of());
            return;
        }

        KieHelper helper = new KieHelper();
        Map<String, PolicyRuleRecord> index = new LinkedHashMap<>();
        for (PolicyRuleRecord rule : effective) {
            helper.addContent(rule.drlContent(), org.kie.api.io.ResourceType.DRL);
            index.put(rule.name(), rule);
        }
        Results results = helper.verify();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("DRL compilation failed: " + results.getMessages());
        }
        KieContainer built = helper.getKieContainer();
        container.set(built);
        ruleIndex.set(Map.copyOf(index));
    }

    public PolicyDecision evaluate(PolicyFact fact) {
        KieContainer kieContainer = container.get();
        if (kieContainer == null) {
            throw new IllegalStateException("No active policy rules loaded");
        }
        return evaluateWithContainer(kieContainer, ruleIndex.get(), fact);
    }

    public PolicyDecision evaluateWithRules(Collection<PolicyRuleRecord> rules, PolicyFact fact) {
        KieContainer kieContainer = buildEphemeral(rules);
        Map<String, PolicyRuleRecord> index = new LinkedHashMap<>();
        for (PolicyRuleRecord rule : rules) {
            index.put(rule.name(), rule);
        }
        return evaluateWithContainer(kieContainer, index, fact);
    }

    public KieContainer buildEphemeral(Collection<PolicyRuleRecord> rules) {
        KieHelper helper = new KieHelper();
        for (PolicyRuleRecord rule : rules) {
            helper.addContent(rule.drlContent(), org.kie.api.io.ResourceType.DRL);
        }
        Results results = helper.verify();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("DRL compilation failed: " + results.getMessages());
        }
        return helper.getKieContainer();
    }

    private PolicyDecision evaluateWithContainer(
            KieContainer kieContainer, Map<String, PolicyRuleRecord> index, PolicyFact fact) {
        PolicyDecision decision = new PolicyDecision();
        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(fact);
            session.insert(decision);
            session.fireAllRules();
        } finally {
            session.dispose();
        }

        if ("DENY".equals(decision.getDecision()) && decision.getReason() != null) {
            PolicyRuleRecord matched = index.get(decision.getReason());
            if (matched == null) {
                matched = index.values().stream()
                        .filter(r -> decision.getReason().equals(r.name()))
                        .findFirst()
                        .orElse(index.values().iterator().next());
            }
            decision.setRuleId(matched.id().toString());
            decision.setRuleVersion(matched.version());
        } else {
            PolicyRuleRecord primary = index.values().iterator().next();
            decision.setRuleId(primary.id().toString());
            decision.setRuleVersion(primary.version());
        }
        return decision;
    }
}
