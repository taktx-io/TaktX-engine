package com.flomaestro.app.workers;

import com.flomaestro.client.annotation.TaktDeployment;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Startup
@ApplicationScoped
@TaktDeployment(resource = "bpmn/task-multi.bpmn")
public class TaskMultiWorker {
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
}
