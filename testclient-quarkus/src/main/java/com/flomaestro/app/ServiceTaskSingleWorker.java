package com.flomaestro.app;

import com.flomaestro.client.BpmnDeployment;
import com.flomaestro.client.ExternalTask;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Startup
@ApplicationScoped
@BpmnDeployment(resource = "bpmn/servicetask-single.gen1.bpmn")
public class ServiceTaskSingleWorker {
  @ExternalTask(element = "service-task-id")
  public ServiceTaskResults doWork(String inputVariable) {
    long start = Instant.now().toEpochMilli();
    System.out.println("ExampleWorker.doWork() called");
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    System.out.println(
        "Finished ExampleWorker.doWork() in " + (Instant.now().toEpochMilli() - start) + "ms");
    return new ServiceTaskResults("Hello from ExampleWorker " + inputVariable);
  }

  @RequiredArgsConstructor
  @Getter
  public static class ServiceTaskResults {
    public final String result;
  }
}
