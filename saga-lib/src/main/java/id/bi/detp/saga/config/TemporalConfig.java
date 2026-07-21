package id.bi.detp.saga.config;

import id.bi.detp.saga.activity.SagaActivitiesImpl;
import id.bi.detp.saga.workflow.IssuanceSagaImpl;
import id.bi.detp.saga.workflow.RedemptionSagaImpl;
import id.bi.detp.saga.workflow.TransferSagaImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class TemporalConfig {

    @Bean
    WorkflowServiceStubs workflowServiceStubs() {
        String target = System.getenv().getOrDefault("TEMPORAL_TARGET", "localhost:7233");
        return WorkflowServiceStubs.newServiceStubs(
                io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs, WorkflowClientOptions.newBuilder().build());
    }

    @Bean
    WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Component
    static class TemporalWorkerStarter implements ApplicationRunner {

        private static final Logger log = LoggerFactory.getLogger(TemporalWorkerStarter.class);

        private final WorkerFactory workerFactory;
        private final SagaActivitiesImpl activities;

        TemporalWorkerStarter(WorkerFactory workerFactory, SagaActivitiesImpl activities) {
            this.workerFactory = workerFactory;
            this.activities = activities;
        }

        @Override
        public void run(ApplicationArguments args) throws InterruptedException {
            Worker worker = workerFactory.newWorker("saga-task-queue");
            worker.registerWorkflowImplementationTypes(
                    IssuanceSagaImpl.class,
                    RedemptionSagaImpl.class,
                    TransferSagaImpl.class
            );
            worker.registerActivitiesImplementations(activities);

            for (int attempt = 1; attempt <= 30; attempt++) {
                try {
                    workerFactory.start();
                    log.info("Temporal worker factory started (attempt {})", attempt);
                    return;
                } catch (Exception e) {
                    log.warn("Temporal not ready (attempt {}/30): {}", attempt, e.getMessage());
                    Thread.sleep(2000);
                }
            }
            log.error("Temporal worker failed to start after 30 attempts — saga intake will not process workflows");
        }
    }
}
