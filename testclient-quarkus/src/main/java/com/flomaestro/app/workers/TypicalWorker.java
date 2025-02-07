package com.flomaestro.app.workers;

import com.flomaestro.client.BpmnDeployment;
import com.flomaestro.client.ExternalTask;
import com.flomaestro.client.ResponseConsumer;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Startup
@ApplicationScoped
@BpmnDeployment(resource = "bpmn/typical.bpmn")
public class TypicalWorker {
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @ExternalTask(element = "benchmark-task-200")
  public void doWork(ResponseConsumer responseConsumer) {
    executor.submit(
        () -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            responseConsumer.respondError(
                false, "Error while sleeping", "SLEEP_ERROR", "SLEEP_ERROR");
            throw new RuntimeException(e);
          }

          responseConsumer.respondSucess(Map.of("result", "success"));
        });
  }

  @ExternalTask(element = "benchmark-task-200-completed")
  public void doWorkCompleted(ResponseConsumer responseConsumer) {
    executor.submit(
        () -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            responseConsumer.respondError(
                false, "Error while sleeping", "SLEEP_ERROR", "SLEEP_ERROR");
            throw new RuntimeException(e);
          }

          responseConsumer.respondSucess(Map.of("result", "success"));
        });
  }
}
