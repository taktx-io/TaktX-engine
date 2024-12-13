package nl.qunit.bpmnmeister.app;

import com.flomaestro.client.ExternalTask;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// @BpmnDeployment(resource = "bpmn/SolutionFulfillment.bpmn")
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
