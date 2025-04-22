package io.taktx.app.workers;

import io.quarkus.runtime.Startup;
import io.taktx.client.annotation.TaktDeployment;
import io.taktx.client.annotation.TaktWorker;
import io.taktx.client.annotation.TaktWorkerMethod;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@TaktDeployment(resource = "bpmn/servicetask-multiinstance-parallel.gen1.bpmn")
@Startup
@ApplicationScoped
@TaktWorker(processDefinitionId = "service-task-multiinstance-parallel")
public class ServiceTaskMultiInstanceParallelWorker {
  @TaktWorkerMethod(taskId = "create-collection-id")
  public CreateCollectionResults createCollection() {
    System.out.println("ServiceTaskMultiInstanceParallelWorker.createCollection()");
    return new CreateCollectionResults(
        IntStream.rangeClosed(1, 1000).boxed().map(i -> "" + i).toList());
  }

  @TaktWorkerMethod(taskId = "service-task-id")
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
