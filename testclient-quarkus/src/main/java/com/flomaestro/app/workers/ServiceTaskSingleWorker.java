package com.flomaestro.app.workers;

import com.flomaestro.client.BpmnDeployment;
import com.flomaestro.client.ExternalTask;
import com.flomaestro.client.ResponseConsumer;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Startup
@ApplicationScoped
@BpmnDeployment(resource = "/bpmn/servicetask-single-clean.bpmn")
public class ServiceTaskSingleWorker {

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @ExternalTask(element = "service-task")
  public void doWork(ResponseConsumer responseConsumer) {
    //    responseConsumer.respondPromise(Duration.ofSeconds(10));

    executor.submit(
        () -> {
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            responseConsumer.respondError(
                false, "Error while sleeping", "SLEEP_ERROR", "SLEEP_ERROR");
            throw new RuntimeException(e);
          }

          responseConsumer.respondSucess();
        });
  }
}
