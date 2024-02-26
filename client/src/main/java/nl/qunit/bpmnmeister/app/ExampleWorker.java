package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.client.BpmnDeployment;
import nl.qunit.bpmnmeister.client.ExternalTask;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;

@BpmnDeployment(resource = "bpmn/servicetask.gen1.bpmn")
@Startup
@ApplicationScoped
public class ExampleWorker {
  @ExternalTask(element = "Activity_0smjpro")
  public void doWork(ExternalTaskTrigger trigger) {
    System.out.println("ExampleWorker.doWork() called");
  }
}
