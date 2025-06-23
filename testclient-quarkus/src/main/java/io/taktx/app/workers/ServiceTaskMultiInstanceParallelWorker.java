/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.workers;

import io.quarkus.runtime.Startup;
import io.taktx.client.annotation.TaktDeployment;
import io.taktx.client.annotation.TaktWorkerMethod;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@TaktDeployment(resource = "bpmn/servicetask-multiinstance-parallel.gen1.bpmn")
@Startup
@ApplicationScoped
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
