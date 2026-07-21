package id.bi.detp.policy.api.dto;

import java.util.List;
import java.util.Map;

public record RuleTestResult(boolean passed, int casesRun, List<Map<String, Object>> failures) {
}
