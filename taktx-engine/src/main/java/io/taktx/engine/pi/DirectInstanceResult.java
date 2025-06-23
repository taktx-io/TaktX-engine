/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pi.model.FlowNodeInstanceInfo;
import io.taktx.engine.pi.model.ProcessInstance;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class DirectInstanceResult {

  private final Queue<FlowNodeInstanceInfo> newFlowNodeInstanceInfos = new ArrayDeque<>();
  private final List<String> sequenceFlows = new ArrayList<>();
  private List<Long> terminateParentPath;
  private final Queue<Long> terminateInstances = new ArrayDeque<>();
  private final Queue<EventSignal> events = new ArrayDeque<>();
  private final Queue<EventSignal> bubbleUpEvents = new ArrayDeque<>();

  private DirectInstanceResult() {}

  public void setTerminateParentPath(List<Long> instancePath) {
    this.terminateParentPath = instancePath != null ? new ArrayList<>(instancePath) : null;
  }

  public List<Long> getTerminateParentPath() {
    return terminateParentPath;
  }

  public static DirectInstanceResult empty() {
    return new DirectInstanceResult();
  }

  public void addNewFlowNodeInstance(
      ProcessInstance processInstance, FlowNodeInstanceInfo flowNodeInstanceInfo) {
    if (flowNodeInstanceInfo.inputSequenceFlowId() != null) {
      if (sequenceFlows.contains(flowNodeInstanceInfo.inputSequenceFlowId())) {
        throw new ProcessInstanceException(
            processInstance,
            flowNodeInstanceInfo.flowNodeInstance(),
            "Straight through processing loop detected for sequenceflow "
                + flowNodeInstanceInfo.inputSequenceFlowId()
                + " in: "
                + sequenceFlows);
      }
      sequenceFlows.add(flowNodeInstanceInfo.inputSequenceFlowId());
    }
    newFlowNodeInstanceInfos.add(flowNodeInstanceInfo);
  }

  public FlowNodeInstanceInfo pollNewFlowNodeInstance() {
    return newFlowNodeInstanceInfos.poll();
  }

  public Long pollTerminateInstance() {
    return terminateInstances.poll();
  }

  public EventSignal pollEvent() {
    return events.poll();
  }

  public void addTerminateInstance(long terminateInstanceId) {
    this.terminateInstances.add(terminateInstanceId);
  }

  public boolean hasDirectTriggers() {
    return !newFlowNodeInstanceInfos.isEmpty()
        || !terminateInstances.isEmpty()
        || !events.isEmpty()
        || terminateParentPath != null;
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
