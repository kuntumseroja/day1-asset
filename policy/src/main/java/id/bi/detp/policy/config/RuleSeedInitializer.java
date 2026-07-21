package id.bi.detp.policy.config;

import id.bi.detp.policy.domain.PolicyRuleRecord;
import id.bi.detp.policy.domain.RuleStatus;
import id.bi.detp.policy.repository.PolicyRuleRepository;
import id.bi.detp.policy.service.PolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

@Component
public class RuleSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RuleSeedInitializer.class);

    private static final List<String> SAMPLE_NAMES = List.of(
            "per_issuance_cap", "daily_cumulative_cap", "tier_limit", "cutoff_window");

    private final PolicyRuleRepository repository;
    private final PolicyService policyService;
    private final Clock clock;

    public RuleSeedInitializer(PolicyRuleRepository repository, PolicyService policyService, Clock clock) {
        this.repository = repository;
        this.policyService = policyService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (repository.findActiveEffective(clock.instant()).isEmpty()) {
            seedSamplesIfMissing();
        }
        policyService.rebuild(repository.findActiveEffective(clock.instant()));
        log.info("Loaded {} active policy rules", repository.findActiveEffective(clock.instant()).size());
    }

    private void seedSamplesIfMissing() throws Exception {
        for (String name : SAMPLE_NAMES) {
            if (repository.countActiveByName(name) > 0) {
                continue;
            }
            String drl = loadSample(name);
            PolicyRuleRecord created = repository.insert(name, drl, "system-seed", clock.instant());
            repository.updateStatus(created.id(), RuleStatus.ACTIVE, "system-seed");
            log.info("Seeded active rule {}", name);
        }
    }

    private String loadSample(String name) throws Exception {
        var resource = new ClassPathResource("samples/" + name + ".drl");
        if (resource.exists()) {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        }
        Path fsPath = Path.of("samples", name + ".drl");
        if (Files.exists(fsPath)) {
            return Files.readString(fsPath, StandardCharsets.UTF_8);
        }
        return Files.readString(Path.of("policy/samples", name + ".drl"), StandardCharsets.UTF_8);
    }
}
