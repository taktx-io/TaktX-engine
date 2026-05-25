/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.CleanupPolicy;
import java.util.Objects;

/** Focused public facet for worker topic management and worker-trigger subscriptions. */
public final class WorkersClient {

  private final TaktXClient client;

  WorkersClient(TaktXClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  public String requestExternalTaskTopic(String externalTaskId) {
    return client.requestExternalTaskTopic(externalTaskId);
  }

  public String requestExternalTaskTopic(
      String externalTaskId, int partitions, CleanupPolicy cleanupPolicy, short replicationFactor) {
    return client.requestExternalTaskTopic(
        externalTaskId, partitions, cleanupPolicy, replicationFactor);
  }

  public void registerExternalTaskConsumer(
      ExternalTaskTriggerConsumer externalTaskTriggerConsumer, String groupId) {
    client.registerExternalTaskConsumer(externalTaskTriggerConsumer, groupId);
  }

  public void registerUserTaskConsumer(UserTaskTriggerConsumer userTaskTriggerConsumer) {
    client.registerUserTaskConsumer(userTaskTriggerConsumer);
  }

  public void deployTaktDeploymentAnnotatedClasses() {
    client.deployTaktDeploymentAnnotatedClasses();
  }

  public AnnotationScanningExternalTaskTriggerConsumer
      annotationScanningExternalTaskTriggerConsumer(
          WorkerBeanInstanceProvider instanceProvider,
          int partitions,
          CleanupPolicy cleanupPolicy,
          short replicationFactor) {
    return new AnnotationScanningExternalTaskTriggerConsumer(
        client.getParameterResolverFactory(),
        client.getResultProcessorFactory(),
        client.getProcessInstanceResponder(),
        instanceProvider,
        this::requestExternalTaskTopic,
        partitions,
        cleanupPolicy,
        replicationFactor);
  }

  public void stopExternalTaskConsumer() {
    client.stopExternalTaskConsumer();
  }
}
