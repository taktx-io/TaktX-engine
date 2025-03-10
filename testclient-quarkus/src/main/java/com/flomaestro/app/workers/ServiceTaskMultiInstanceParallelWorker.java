package com.flomaestro.app.workers;

import com.flomaestro.client.annotation.TaktWorker;
import com.flomaestro.client.annotation.TaktWorkerMethod;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// @BpmnDeployment(resource = "bpmn/servicetask-multiinstance-parallel.gen1.bpmn")
@Startup
@ApplicationScoped
@TaktWorker(processDefinitionId = "servicetask-multiinstance-parallel")
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
