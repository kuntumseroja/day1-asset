package id.bi.detp.policy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.bi.detp.policy.api.dto.*;
import id.bi.detp.policy.domain.PolicyDecision;
import id.bi.detp.policy.domain.PolicyFact;
import id.bi.detp.policy.domain.PolicyRuleRecord;
import id.bi.detp.policy.domain.RuleStatus;
import id.bi.detp.policy.repository.PolicyRuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class RuleService {

    private final PolicyRuleRepository repository;
    private final PolicyService policyService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Path trafficReplayPath;

    public RuleService(
            PolicyRuleRepository repository,
            PolicyService policyService,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${policy.traffic-replay-path}") String trafficReplayPath) {
        this.repository = repository;
        this.policyService = policyService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.trafficReplayPath = Path.of(trafficReplayPath).toAbsolutePath().normalize();
    }

    public EvaluationResult evaluate(EvaluationRequest request) {
        PolicyFact fact = toFact(request);
        PolicyDecision decision = policyService.evaluate(fact);
        return toResult(decision);
    }

    public List<PolicyRuleDto> listRules(Optional<String> status) {
        Optional<RuleStatus> filter = status.map(RuleStatus::from);
        return repository.findAll(filter).stream().map(this::toDto).toList();
    }

    public PolicyRuleDto getRule(UUID id) {
        return toDto(findRule(id));
    }

    public PolicyRuleDto createRule(CreateRuleRequest request) {
        Instant effectiveFrom = request.effectiveFrom() != null ? request.effectiveFrom() : clock.instant();
        PolicyRuleRecord created = repository.insert(
                request.name(), request.drlContent(), request.authorId(), effectiveFrom);
        repository.insertAudit(created.id(), "CREATE", request.authorId(), "{}");
        return toDto(created);
    }

    public PolicyRuleDto transition(UUID id, RuleTransition transition) {
        PolicyRuleRecord rule = findRule(id);
        RuleStatus next = switch (transition.action()) {
            case "SUBMIT_TEST" -> RuleStatus.TESTED;
            case "APPROVE" -> {
                if (transition.actorId().equals(rule.authorId())) {
                    repository.insertAudit(id, "APPROVE_REJECTED", transition.actorId(),
                            "{\"reason\":\"author_cannot_approve\"}");
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Author cannot approve own rule");
                }
                yield RuleStatus.APPROVED;
            }
            case "ACTIVATE" -> {
                repository.deactivateByNameExcept(id, rule.name());
                publishConfigEvent(rule, transition.actorId());
                yield RuleStatus.ACTIVE;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action");
        };

        String approverId = "APPROVE".equals(transition.action()) ? transition.actorId() : null;
        PolicyRuleRecord updated = repository.updateStatus(id, next, approverId);
        repository.insertAudit(id, transition.action(), transition.actorId(), "{}");

        if (next == RuleStatus.ACTIVE) {
            reloadActiveRules();
        }
        return toDto(updated);
    }

    public RuleTestResult testRule(UUID id) {
        PolicyRuleRecord underTest = findRule(id);
        List<PolicyRuleRecord> active = repository.findActiveEffective(clock.instant());
        List<PolicyRuleRecord> ruleset = mergeRuleset(active, underTest);
        List<Map<String, Object>> cases = loadTrafficCases();
        List<Map<String, Object>> failures = new ArrayList<>();

        for (Map<String, Object> testCase : cases) {
            PolicyFact fact = factFromCase(testCase);
            PolicyDecision decision = policyService.evaluateWithRules(ruleset, fact);
            String expected = (String) testCase.get("expected");
            if (!expected.equals(decision.getDecision())) {
                failures.add(Map.of(
                        "participantId", testCase.get("participantId"),
                        "amount", testCase.get("amount"),
                        "expected", expected,
                        "actual", decision.getDecision(),
                        "reason", decision.getReason() != null ? decision.getReason() : ""));
            }
        }
        return new RuleTestResult(failures.isEmpty(), cases.size(), failures);
    }

    public void reloadActiveRules() {
        policyService.rebuild(repository.findActiveEffective(clock.instant()));
    }

    private List<PolicyRuleRecord> mergeRuleset(List<PolicyRuleRecord> active, PolicyRuleRecord underTest) {
        Map<String, PolicyRuleRecord> merged = new LinkedHashMap<>();
        for (PolicyRuleRecord rule : active) {
            merged.put(rule.name(), rule);
        }
        merged.put(underTest.name(), underTest);
        return List.copyOf(merged.values());
    }

    private List<Map<String, Object>> loadTrafficCases() {
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    Files.readString(trafficReplayPath), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cases = (List<Map<String, Object>>) payload.get("cases");
            return cases;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load traffic replay: " + trafficReplayPath, e);
        }
    }

    private PolicyRuleRecord findRule(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
    }

    private PolicyFact toFact(EvaluationRequest request) {
        return new PolicyFact(
                request.participantId(),
                request.tier(),
                request.amount(),
                request.dailyCumulative(),
                request.timestamp());
    }

    private PolicyFact factFromCase(Map<String, Object> testCase) {
        return new PolicyFact(
                (String) testCase.get("participantId"),
                (String) testCase.get("tier"),
                ((Number) testCase.get("amount")).longValue(),
                ((Number) testCase.get("dailyCumulative")).longValue(),
                Instant.parse((String) testCase.get("timestamp")));
    }

    private EvaluationResult toResult(PolicyDecision decision) {
        return new EvaluationResult(
                decision.getDecision(),
                decision.getReason(),
                decision.getRuleId(),
                decision.getRuleVersion());
    }

    private PolicyRuleDto toDto(PolicyRuleRecord rule) {
        return new PolicyRuleDto(
                rule.id().toString(),
                rule.name(),
                rule.drlContent(),
                rule.version(),
                rule.status().name(),
                rule.effectiveFrom(),
                rule.authorId(),
                rule.approverId());
    }

    private void publishConfigEvent(PolicyRuleRecord rule, String actorId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "ruleId", rule.id().toString(),
                    "ruleName", rule.name(),
                    "ruleVersion", rule.version(),
                    "status", RuleStatus.ACTIVE.name(),
                    "effectiveFrom", rule.effectiveFrom().toString(),
                    "publishedAt", clock.instant().toString(),
                    "publishedBy", actorId));
            repository.insertAudit(rule.id(), "PUBLISH", actorId, payload);
            repository.publishOutbox(rule.id(), payload);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
