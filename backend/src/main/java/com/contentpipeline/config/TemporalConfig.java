package com.contentpipeline.config;

import com.contentpipeline.workflow.PipelineActivitiesImpl;
import com.contentpipeline.workflow.StoryPipelineWorkflowImpl;
import com.contentpipeline.workflow.WorkflowStarter;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*Allegedly temporal is a way to make long running programs keep running 
even in crashes so a temporal worker is just given tasks and a server coordinates them  */

@Configuration
public class TemporalConfig {
    /*“Set temporalHost to the config value named temporal.host.
    But if that value does not exist, use localhost:7233 as the default. */
    @Value("${temporal.host:localhost:7233}")
    private String temporalHost;

    @Value("${temporal.namespace:default}")
    private String namespace;

    //This is a factoru for 
    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalHost)
                .build()
        );
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build()
        );
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client,
                                       PipelineActivitiesImpl activities) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(WorkflowStarter.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(StoryPipelineWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
        return factory;
    }
}
