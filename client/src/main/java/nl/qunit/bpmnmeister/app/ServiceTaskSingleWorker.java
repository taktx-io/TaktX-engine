package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.client.ExternalTask;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;

// @BpmnDeployment(resource = "bpmn/servicetask-single.gen1.bpmn")
@ApplicationScoped
@Startup
public class ServiceTaskSingleWorker {
  @ExternalTask(element = "service-task-id")
  public ServiceTaskResults doWork(ExternalTaskTrigger trigger, String inputVariable) {
    System.out.println("ExampleWorker.doWork() called");
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    System.out.println("Finished ExampleWorker.doWork()");
    return new ServiceTaskResults("Hello from ExampleWorker " + inputVariable);
  }

  @RequiredArgsConstructor
  @Getter
  public static class ServiceTaskResults {
    public final String result;
  }
}
