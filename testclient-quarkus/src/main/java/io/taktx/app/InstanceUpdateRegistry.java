/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app;

import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.TaktClient;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceState;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class InstanceUpdateRegistry {
  private static final int MAX_STORED_INSTANCES = 100000;
  private final Map<UUID, InstanceUpdateRecord> processInstanceUpdates = new ConcurrentHashMap<>();
  private final Map<UUID, List<InstanceUpdateRecord>> flowNodeInstanceUpdates =
      new ConcurrentHashMap<>();

  private final Map<ProcessDefinitionKey, AtomicInteger> processInstanceCountsStarted =
      new ConcurrentHashMap<>();
  private final Map<ProcessDefinitionKey, AtomicInteger> processInstanceCountsCompleted =
      new ConcurrentHashMap<>();

  private final Map<ProcessDefinitionKey, List<UUID>> processInstanceIdUpdates =
      new ConcurrentHashMap<>();

  private final TaktClient taktClient;
  private final List<InstanceUpdateConsumer> instanceUpdateConsumers = new ArrayList<>();

  @PostConstruct
  void init() {
    taktClient.registerInstanceUpdateConsumer(this::handleInstanceUpdate);
  }

  private void handleInstanceUpdate(InstanceUpdateRecord instanceUpdateRecord) {
    UUID processInstanceId = instanceUpdateRecord.getProcessInstanceId();
    if (instanceUpdateRecord.getUpdate()
        instanceof ProcessInstanceUpdateDTO processInstanceUpdate) {
      // Extract process definition ID from update
      ProcessDefinitionKey processDefinitionKey = processInstanceUpdate.getProcessDefinitionKey();
      List<UUID> processInstanceIdList = processInstanceIdUpdates.get(processDefinitionKey);
      if (processInstanceIdList == null) {
        processInstanceIdList = new ArrayList<>();
        processInstanceIdUpdates.put(processDefinitionKey, processInstanceIdList);
        processInstanceCountsStarted.put(processDefinitionKey, new AtomicInteger(0));
        processInstanceCountsCompleted.put(processDefinitionKey, new AtomicInteger(0));
      }
      if (!processInstanceIdList.contains(processInstanceId)) {
        processInstanceIdList.add(processInstanceId);
        if (processInstanceUpdate.getFlowNodeInstances().getState()
            == ProcessInstanceState.ACTIVE) {
          processInstanceCountsStarted.get(processDefinitionKey).incrementAndGet();
        }
      }
      if (processInstanceUpdate.getFlowNodeInstances().getState().isFinished()) {
        processInstanceCountsCompleted.get(processDefinitionKey).incrementAndGet();
      }
      if (processInstanceIdList.size() > MAX_STORED_INSTANCES) {
        processInstanceIdList.removeFirst();
      }
      processInstanceUpdates.put(processInstanceId, instanceUpdateRecord);
      instanceUpdateConsumers.forEach(
          consumer ->
              consumer.processInstanceUpdate(
                  instanceUpdateRecord.getTimestamp(), processInstanceId, processInstanceUpdate));
      log.info(
          "Stored {} process instances for definition : " + processInstanceIdList.size(),
          processDefinitionKey);
    } else if (instanceUpdateRecord.getUpdate()
        instanceof FlowNodeInstanceUpdateDTO flowNodeInstanceUpdate) {
      List<InstanceUpdateRecord> instances =
          this.flowNodeInstanceUpdates.computeIfAbsent(processInstanceId, k -> new ArrayList<>());
      instances.add(instanceUpdateRecord);
      instanceUpdateConsumers.forEach(
          consumer ->
              consumer.flowNodeInstanceUpdate(
                  instanceUpdateRecord.getTimestamp(), processInstanceId, flowNodeInstanceUpdate));
    }
  }

  public Set<ProcessDefinitionKey> getProcessDefinitionKeys() {
    return processInstanceIdUpdates.keySet();
  }

  public Map<ProcessDefinitionKey, AtomicInteger> getProcessDefinitionCountsStarted() {
    return processInstanceCountsStarted;
  }

  public Map<ProcessDefinitionKey, AtomicInteger> getProcessDefinitionCountsCompleted() {
    return processInstanceCountsCompleted;
  }

  public void registerInstanceUpdateConsumer(InstanceUpdateConsumer instanceUpdateConsumer) {
    instanceUpdateConsumers.add(instanceUpdateConsumer);
  }

  public InstanceUpdateRecord getProcessInstance(UUID processInstanceId) {
    return processInstanceUpdates.get(processInstanceId);
  }

  public List<InstanceUpdateRecord> getFlowNodeInstancesByProcessInstance(
      UUID processInstanceUuid) {
    return this.flowNodeInstanceUpdates.get(processInstanceUuid);
  }

  public List<InstanceUpdateRecord> getProcessInstancesByDefinition(
      ProcessDefinitionKey key, int limit) {
    List<UUID> uuids = processInstanceIdUpdates.getOrDefault(key, new ArrayList<>());
    List<InstanceUpdateRecord> instances = new ArrayList<>();
    for (UUID uuid : uuids) {
      InstanceUpdateRecord instanceUpdateRecord = processInstanceUpdates.get(uuid);
      instances.add(instanceUpdateRecord);
    }
    return instances.reversed();
  }
}
