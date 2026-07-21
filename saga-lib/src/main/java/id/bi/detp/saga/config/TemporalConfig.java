package id.bi.detp.saga.config;

import id.bi.detp.saga.activity.SagaActivitiesImpl;
import id.bi.detp.saga.workflow.IssuanceSagaImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    WorkerFactory workerFactory(WorkflowClient client, SagaActivitiesImpl activities) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker("saga-task-queue");
        worker.registerWorkflowImplementationTypes(IssuanceSagaImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
        return factory;
    }
}
