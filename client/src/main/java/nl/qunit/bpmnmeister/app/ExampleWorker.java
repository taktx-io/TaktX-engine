package nl.qunit.bpmnmeister.app;

import java.util.Map;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import nl.qunit.bpmnmeister.client.BpmnDeployment;
import nl.qunit.bpmnmeister.client.ExternalTask;
import nl.qunit.bpmnmeister.client.ExternalTaskTriggerCallback;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;

@BpmnDeployment(resource="bpmn/servicetask.gen1.bpmn")
@Startup
@ApplicationScoped
public class ExampleWorker {
  @ExternalTask(element = "Activity_0smjpro")
  public void doWork(ExternalTaskTrigger trigger, ExternalTaskTriggerCallback callback) {
    System.out.println("ExampleWorker.doWork() called");
    Integer counter = (Integer) trigger.getVariables().computeIfAbsent("counter", k -> 0);
    counter++;
    callback.complete(Map.of("counter", counter));
  }
}
