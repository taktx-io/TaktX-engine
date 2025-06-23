/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app.workers;

import io.quarkus.runtime.Startup;
import io.taktx.client.ExternalTaskInstanceResponder;
import io.taktx.client.annotation.TaktDeployment;
import io.taktx.client.annotation.TaktWorkerMethod;
import io.taktx.client.annotation.Variable;
import io.taktx.dto.ExternalTaskTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Startup
@ApplicationScoped
@TaktDeployment(resource = "bpmn/typical.bpmn")
public class TypicalWorker {
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @TaktWorkerMethod(taskId = "benchmark-task-200", autoComplete = false)
  public void doWork(
      ExternalTaskInstanceResponder externalTaskInstanceResponder,
      ExternalTaskTriggerDTO externalTaskInstanceDTO,
      Map<String, Object> variables,
      @Variable("varx1") String variable1,
      String varx2) {
    executor.submit(
        () -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            externalTaskInstanceResponder.respondError(
                false, "Error while sleeping", "SLEEP_ERROR");
            Thread.currentThread().interrupt();
          }

          externalTaskInstanceResponder.respondSuccess(Map.of("result", "success"));
        });
  }

  @TaktWorkerMethod(taskId = "benchmark-task-200-completed")
  public boolean doWorkCompleted() {
    executor.submit(
        () -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return true;
        });
    return true;
  }
}
