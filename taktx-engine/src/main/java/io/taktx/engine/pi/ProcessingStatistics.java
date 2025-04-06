/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Singleton
@Getter
@RequiredArgsConstructor
public class ProcessingStatistics {
  private final MeterRegistry meterRegistry;
  private final Map<UUID, Sample> processInstanceTimers = new ConcurrentHashMap<>();
  private Counter processInstancesStarted;
  private Counter processInstancesFinished;
  private Counter flowNodesStarted;
  private Counter flowNodesContinued;
  private Counter flowNodesFinished;

  @PostConstruct
  void init() {
    processInstancesStarted = meterRegistry.counter("takt.engine.process_instances_started");
    processInstancesFinished = meterRegistry.counter("takt.engine.process_instances_finished");
    flowNodesStarted = meterRegistry.counter("takt.engine.flow_nodes_started");
    flowNodesContinued = meterRegistry.counter("takt.engine.flow_nodes_continued");
    flowNodesFinished = meterRegistry.counter("takt.engine.flow_nodes_finished");
  }

  public void startTimerForProcessInstance(UUID processInstanceKey) {
    Timer.Sample sample = Timer.start(meterRegistry);
    processInstanceTimers.put(processInstanceKey, sample);
  }

  public void stopTimerForProcessInstance(UUID processInstanceKey, String processDefinitionKey) {
    Timer.Sample sample = processInstanceTimers.remove(processInstanceKey);
    if (sample != null) {
      Timer timer =
          meterRegistry.timer(
              "takt.engine.process_instance_duration",
              "processDefinitionKey",
              processDefinitionKey);
      sample.stop(timer);
    }
  }

  public void increaseProcessInstancesStarted() {
    processInstancesStarted.increment();
  }

  public void increaseProcessInstancesFinished() {
    processInstancesFinished.increment();
  }

  public void increaseFlowNodesStarted() {
    flowNodesStarted.increment();
  }

  public void increaseFlowNodesFinished() {
    flowNodesFinished.increment();
  }

  public void increaseFlowNodesContinued() {
    flowNodesContinued.increment();
  }
}
