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
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class DirectInstanceResult {

  private final List<String> sequenceFlows = new ArrayList<>();
  private final Queue<StartFlowNodeInstanceInfo> newFlowNodeInstances = new ArrayDeque<>();
  private final Queue<ContinueFlowNodeInstanceInfo> continueInstances = new ArrayDeque<>();
  private final Queue<Long> abortInstances = new ArrayDeque<>();
  private final Queue<Long> cancelInstances = new ArrayDeque<>();
  private final Queue<EventSignal> events = new ArrayDeque<>();
  private final Queue<EventSignal> bubbleUpEvents = new ArrayDeque<>();

  private DirectInstanceResult() {}

  public static DirectInstanceResult empty() {
    return new DirectInstanceResult();
  }

  public void addNewFlowNodeInstance(
      ProcessInstance processInstance, StartFlowNodeInstanceInfo startFlowNodeInstanceInfo) {
    if (startFlowNodeInstanceInfo.inputSequenceFlowId() != null) {
      if (sequenceFlows.contains(startFlowNodeInstanceInfo.inputSequenceFlowId())) {
        throw new ProcessInstanceException(
            processInstance,
            startFlowNodeInstanceInfo.flowNodeInstance(),
            "Straight through processing loop detected for sequenceflow "
                + startFlowNodeInstanceInfo.inputSequenceFlowId()
                + " in: "
                + sequenceFlows);
      }
      sequenceFlows.add(startFlowNodeInstanceInfo.inputSequenceFlowId());
    }
    newFlowNodeInstances.add(startFlowNodeInstanceInfo);
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

  public Long pollAbortInstance() {
    return abortInstances.poll();
  }

  public Long pollCancelInstance() {
    return cancelInstances.poll();
  }

  public EventSignal pollEvent() {
    return events.poll();
  }

  public void addAbortInstance(long abortInstanceId) {
    this.abortInstances.add(abortInstanceId);
  }

  public void addCancelInstance(long cancelInstanceId) {
    this.cancelInstances.add(cancelInstanceId);
  }

  public boolean hasDirectTriggers() {
    return !newFlowNodeInstances.isEmpty()
        || !continueInstances.isEmpty()
        || !abortInstances.isEmpty()
        || !cancelInstances.isEmpty()
        || !events.isEmpty();
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
