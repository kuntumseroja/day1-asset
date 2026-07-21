package id.bi.detp.recon.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic reconBreakTopic() {
        return TopicBuilder.name("recon.break").partitions(1).replicas(1).build();
    }
}
