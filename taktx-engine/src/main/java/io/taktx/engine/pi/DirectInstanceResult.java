/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.model.ContinueFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import lombok.Getter;

public class DirectInstanceResult {

  private final List<String> sequenceFlows = new ArrayList<>();
  private final ArrayDeque<StartFlowNodeInstanceInfo> newFlowNodeInstances = new ArrayDeque<>();
  private final Queue<ContinueFlowNodeInstanceInfo> continueInstances = new ArrayDeque<>();
  private final Queue<FlowNodeInstance<?>> abortInstances = new ArrayDeque<>();
  private final Queue<EventSignal> events = new ArrayDeque<>();
  private final Queue<EventSignal> bubbleUpEvents = new ArrayDeque<>();
  @Getter private boolean abortScope = false;

  private DirectInstanceResult() {}

  public static DirectInstanceResult empty() {
    return new DirectInstanceResult();
  }

  public void addNewFlowNodeInstance(StartFlowNodeInstanceInfo startFlowNodeInstanceInfo) {
    if (startFlowNodeInstanceInfo.inputSequenceFlowId() != null) {
      if (sequenceFlows.contains(startFlowNodeInstanceInfo.inputSequenceFlowId())) {
        startFlowNodeInstanceInfo
            .flowNodeInstance()
            .raiseIncident(
                "Straight through processing loop detected for sequenceflow "
                    + startFlowNodeInstanceInfo.inputSequenceFlowId()
                    + " in: "
                    + sequenceFlows);
        return;
      }
      sequenceFlows.add(startFlowNodeInstanceInfo.inputSequenceFlowId());
    }
    newFlowNodeInstances.add(startFlowNodeInstanceInfo);
  }

  public List<String> getSequenceFlowsFromNewFlowNodeInstances() {
    return newFlowNodeInstances.stream()
        .map(StartFlowNodeInstanceInfo::inputSequenceFlowId)
        .toList();
  }

  public StartFlowNodeInstanceInfo pollNewFlowNodeInstance() {
    return newFlowNodeInstances.poll();
  }

  public ContinueFlowNodeInstanceInfo pollContinueInstance() {
    return continueInstances.poll();
  }

  public void addContinueInstance(ContinueFlowNodeInstanceInfo continueInstanceInfo) {
    this.continueInstances.add(continueInstanceInfo);
  }

  public FlowNodeInstance<?> pollAbortInstance() {
    return abortInstances.poll();
  }

  public EventSignal pollEvent() {
    return events.poll();
  }

  public void addAbortInstance(FlowNodeInstance<?> abortInstance) {
    this.abortInstances.add(abortInstance);
  }

  public boolean hasDirectTriggers() {
    return !newFlowNodeInstances.isEmpty()
        || !continueInstances.isEmpty()
        || !abortInstances.isEmpty()
        || !events.isEmpty()
        || abortScope;
  }

  public void setAbortScope() {
    this.abortScope = true;
  }

  public void resetAbortScope() {
    abortScope = false;
  }

  public void addBubbleUpEvent(EventSignal event) {
    bubbleUpEvents.add(event);
  }

  public EventSignal pollBubbleUpEvent() {
    return bubbleUpEvents.poll();
  }

  public void addEvent(EventSignal event) {
    events.add(event);
  }
}
