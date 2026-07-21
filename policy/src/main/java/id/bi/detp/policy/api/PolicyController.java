package id.bi.detp.policy.api;

import id.bi.detp.policy.api.dto.*;
import id.bi.detp.policy.service.RuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PolicyController {

    private final RuleService ruleService;

    public PolicyController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping("/policy/evaluate")
    public EvaluationResult evaluate(@RequestBody EvaluationRequest request) {
        return ruleService.evaluate(request);
    }

    @GetMapping("/rules")
    public List<PolicyRuleDto> listRules(@RequestParam Optional<String> status) {
        return ruleService.listRules(status);
    }

    @PostMapping("/rules")
    public ResponseEntity<PolicyRuleDto> createRule(@RequestBody CreateRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.createRule(request));
    }

    @GetMapping("/rules/{id}")
    public PolicyRuleDto getRule(@PathVariable UUID id) {
        return ruleService.getRule(id);
    }

    @PatchMapping("/rules/{id}")
    public PolicyRuleDto transition(@PathVariable UUID id, @RequestBody RuleTransition transition) {
        return ruleService.transition(id, transition);
    }

    @PostMapping("/rules/{id}/test")
    public RuleTestResult testRule(@PathVariable UUID id) {
        return ruleService.testRule(id);
    }
}
