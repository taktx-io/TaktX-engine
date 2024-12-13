package com.flomaestro.app;

import com.flomaestro.client.BpmnDeployment;
import com.flomaestro.client.ExternalTask;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Startup
@ApplicationScoped
@BpmnDeployment(resource = "bpmn/servicetask-single.gen1.bpmn")
public class ServiceTaskSingleWorker {
  @ExternalTask(element = "service-task-id")
  public ServiceTaskResults doWork(String inputVariable) {
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return new ServiceTaskResults("Hello from ExampleWorker " + inputVariable);
  }

  @RequiredArgsConstructor
  @Getter
  public static class ServiceTaskResults {
    public final String result;
  }
}
