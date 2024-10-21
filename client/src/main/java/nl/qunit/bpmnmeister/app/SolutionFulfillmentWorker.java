package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.client.ExternalTask;

// @BpmnDeployment(resource = "bpmn/SolutionFulfillment.bpmn")
@ApplicationScoped
@Startup
public class SolutionFulfillmentWorker {
  @ExternalTask(element = "determine-timeout")
  public DetermineTimeoutResults doWork() {
    return new DetermineTimeoutResults("Hello from ExampleWorker ");
  }

  @RequiredArgsConstructor
  @Getter
  public static class DetermineTimeoutResults {
    public final String result;
  }
}
