package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.stream.IntStream;
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
    return new CreateCollectionResults(
        IntStream.rangeClosed(1, 1000).boxed().map(i -> "" + i).toList());
  }

  @ExternalTask(element = "service-task-id")
  public ServiceTaskResults serviceTask(String inputElement) {
    System.out.println(
        "ServiceTaskMultiInstanceParallelWorker.serviceTask() called with inputElement: "
            + inputElement);
    return new ServiceTaskResults("result-" + inputElement);
  }

  @RequiredArgsConstructor
  @Getter
  public static class ServiceTaskResults {
    public final String outputElement;
  }

  @RequiredArgsConstructor
  @Getter
  public static class CreateCollectionResults {
    public final List<String> inputCollection;
  }
}
