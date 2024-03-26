package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.client.BpmnDeployment;
import nl.qunit.bpmnmeister.client.ExternalTask;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;

@BpmnDeployment(resource = "bpmn/subprocess-single.gen1.bpmn")
@ApplicationScoped
@Startup
public class SubprocessSingleWorker {
  @ExternalTask(element = "service-task-id")
  public ServiceTaskResults doWork(ExternalTaskTrigger trigger, String inputVariable) {
    System.out.println("SubprocessSingleWorker.doWork() called with var1: " + inputVariable);
    return new ServiceTaskResults("Hello from ExampleWorker " + inputVariable);
  }

  @RequiredArgsConstructor
  @Getter
  public static class ServiceTaskResults {
    public final String result;
  }
}
