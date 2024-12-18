package com.flomaestro.app;

import com.flomaestro.client.BpmnDeployment;
import com.flomaestro.client.ExternalTask;
import com.flomaestro.client.ResponseConsumer;
import com.flomaestro.client.Variable;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Startup
@ApplicationScoped
@BpmnDeployment(resource = "bpmn/servicetask-single.gen1.bpmn")
public class ServiceTaskSingleWorker {

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @ExternalTask(element = "service-task-id")
  public void doWork(
      @Variable("inputVariable") String inputVariable, ResponseConsumer responseConsumer) {
    responseConsumer.respondPromise(Duration.ofSeconds(10));

    executor.submit(
        () -> {
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            responseConsumer.respondError(
                false, "Error while sleeping", "SLEEP_ERROR", "SLEEP_ERROR");
            throw new RuntimeException(e);
          }

          ServiceTaskResults serviceTaskResults =
              new ServiceTaskResults("Hello from ExampleWorker " + inputVariable);

          responseConsumer.respondSucess(serviceTaskResults);
        });
  }

  @RequiredArgsConstructor
  @Getter
  public static class ServiceTaskResults {

    public final String result;
  }
}
