package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.client.BpmnDeployment;
import nl.qunit.bpmnmeister.client.ExternalTask;

@BpmnDeployment(resource = "bpmn/servicetask-multiinstance-parallel.gen1.bpmn")
@ApplicationScoped
@Startup
public class ServiceTaskMultiInstanceParallelWorker {
  @ExternalTask(element = "create-collection-id")
  public CreateCollectionResults createCollection() {
    System.out.println("ServiceTaskMultiInstanceParallelWorker.createCollection()");
    return new CreateCollectionResults(List.of("a", "b", "c"));
  }

  @ExternalTask(element = "service-task-id")
  public void serviceTask(String inputElement) {
    System.out.println(
        "ServiceTaskMultiInstanceParallelWorker.serviceTask() called with inputElement: "
            + inputElement);
  }

  @RequiredArgsConstructor
  @Getter
  public static class ServiceTaskResults {
    public final String result;
  }

  @RequiredArgsConstructor
  @Getter
  public static class CreateCollectionResults {
    public final List<String> inputCollection;
  }
}
