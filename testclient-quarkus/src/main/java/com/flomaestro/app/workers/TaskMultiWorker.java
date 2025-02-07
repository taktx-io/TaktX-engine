package com.flomaestro.app.workers;

import com.flomaestro.client.BpmnDeployment;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Startup
@ApplicationScoped
@BpmnDeployment(resource = "bpmn/task-multi.bpmn")
public class TaskMultiWorker {
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
}
