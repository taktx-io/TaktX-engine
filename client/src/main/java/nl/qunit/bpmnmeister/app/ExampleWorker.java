package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.client.BpmnDeployment;
import nl.qunit.bpmnmeister.client.ExternalTask;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;

@BpmnDeployment(resource = "bpmn/servicetask.gen1.bpmn")
@ApplicationScoped
@Startup
public class ExampleWorker {
  @ExternalTask(element = "ServiceTaskId")
  public ServiceTaskResults doWork(ExternalTaskTrigger trigger, String var1) {
    System.out.println("ExampleWorker.doWork() called with var1: " + var1);
    return new ServiceTaskResults("Hello from ExampleWorker " + var1);
  }

  @RequiredArgsConstructor
  @Getter
  public static class ServiceTaskResults {
    public final String result;
  }
}
