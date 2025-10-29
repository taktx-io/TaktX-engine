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
import io.taktx.client.annotation.Deployment;
import io.taktx.client.annotation.TaktWorkerMethod;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Startup
@ApplicationScoped
@Deployment(resources = "/bpmn/servicetask-single-clean.bpmn")
@Slf4j
public class ServiceTaskSingleWorker {

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @TaktWorkerMethod(taskId = "service-task", autoComplete = false)
  public void doWork(ExternalTaskInstanceResponder externalTaskInstanceResponder) {

    executor.submit(
        () -> {
          try {
            log.info("Service-task-worker started");
            Thread.sleep(50);
          } catch (InterruptedException e) {
            externalTaskInstanceResponder.respondError(
                false, "Error while sleeping", "SLEEP_ERROR");
            Thread.currentThread().interrupt();
          }

          log.info("Service-task-worker stopped");
          externalTaskInstanceResponder.respondSuccess();
        });
  }
}
