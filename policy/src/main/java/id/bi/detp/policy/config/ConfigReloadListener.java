package id.bi.detp.policy.config;

import id.bi.detp.policy.service.RuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigReloadListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigReloadListener.class);

    private final RuleService ruleService;

    public ConfigReloadListener(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @KafkaListener(topics = "config.events", groupId = "policy-service")
    public void onConfigEvent(String payload) {
        log.info("config.events received, rebuilding KieContainer");
        ruleService.reloadActiveRules();
    }
}
