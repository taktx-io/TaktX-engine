package com.flomaestro.app.workers;

import com.flomaestro.client.ExternalTaskInstanceResponder;
import com.flomaestro.client.annotation.TaktDeployment;
import com.flomaestro.client.annotation.TaktWorker;
import com.flomaestro.client.annotation.TaktWorkerMethod;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Startup
@ApplicationScoped
@TaktDeployment(resource = "/bpmn/servicetask-single-clean.bpmn")
@TaktWorker(processDefinitionId = "service-task-single")
public class ServiceTaskSingleWorker {

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @TaktWorkerMethod(taskId = "service-task")
  public void doWork(ExternalTaskInstanceResponder externalTaskInstanceResponder) {

    executor.submit(
        () -> {
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            externalTaskInstanceResponder.respondError(
                false, "Error while sleeping", "SLEEP_ERROR", "SLEEP_ERROR");
            throw new RuntimeException(e);
          }

          externalTaskInstanceResponder.respondSuccess();
        });
  }
}
