package com.flomaestro.app.workers;

import com.flomaestro.client.ExternalTaskInstanceResponder;
import com.flomaestro.client.annotation.TaktDeployment;
import com.flomaestro.client.annotation.TaktWorker;
import com.flomaestro.client.annotation.TaktWorkerMethod;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Startup
@ApplicationScoped
@TaktDeployment(resource = "bpmn/typical.bpmn")
@TaktWorker(processDefinitionId = "benchmark")
public class TypicalWorker {
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @TaktWorkerMethod(taskId = "benchmark-task-200")
  public void doWork(ExternalTaskInstanceResponder externalTaskInstanceResponder) {
    executor.submit(
        () -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            externalTaskInstanceResponder.respondError(
                false, "Error while sleeping", "SLEEP_ERROR", "SLEEP_ERROR");
            throw new RuntimeException(e);
          }

          externalTaskInstanceResponder.respondSuccess(Map.of("result", "success"));
        });
  }

  @TaktWorkerMethod(taskId = "benchmark-task-200-completed")
  public void doWorkCompleted(ExternalTaskInstanceResponder externalTaskInstanceResponder) {
    executor.submit(
        () -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            externalTaskInstanceResponder.respondError(
                false, "Error while sleeping", "SLEEP_ERROR", "SLEEP_ERROR");
            throw new RuntimeException(e);
          }

          externalTaskInstanceResponder.respondSuccess(Map.of("result", "success"));
        });
  }
}
