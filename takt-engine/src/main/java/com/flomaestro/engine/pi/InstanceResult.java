/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.EventSignal;
import com.flomaestro.engine.pd.model.NewStartCommand;
import com.flomaestro.engine.pi.model.ExternalTaskInfo;
import com.flomaestro.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.engine.pi.model.ScheduledContinuationInfo;
import com.flomaestro.engine.pi.model.ScheduledExternalTaskTriggerTimeoutInfo;
import com.flomaestro.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InstanceResult {

  private final Queue<EventSignal> bubbleUpEvents = new ArrayDeque<>();
  private final Queue<InstanceUpdate> instanceUpdates = new ArrayDeque<>();
  private final Queue<ExternalTaskInfo> externalTaskRequests = new ArrayDeque<>();
  private final Queue<NewStartCommand> newStartCommands = new ArrayDeque<>();
  private final Queue<UUID> newTerminateCommands = new ArrayDeque<>();
  private final Queue<ContinueFlowElementTriggerDTO> continuations = new ArrayDeque<>();
  private final Queue<NewCorrelationSubscriptionMessageEventInfo>
      newCorrelationSubscriptionMessageEventInfos = new ArrayDeque<>();
  private final Queue<TerminateCorrelationSubscriptionMessageEventInfo>
      terminateCorrelationSubscriptionMessageEventInfos = new ArrayDeque<>();
  private final Queue<ScheduledContinuationInfo> scheduledContinuationInfos = new ArrayDeque<>();
  private final Queue<ScheduleKeyDTO> cancelSchedules = new ArrayDeque<>();
  private final Queue<ScheduledExternalTaskTriggerTimeoutInfo>
      scheduledExternalTaskTriggerTimeouts = new ArrayDeque<>();

  public static InstanceResult empty() {
    return new InstanceResult();
  }

  public void addInstanceUpdate(InstanceUpdate instanceUpdate) {
    instanceUpdates.add(instanceUpdate);
  }

  public void addExternalTaskRequest(ExternalTaskInfo externalTaskInfo) {
    externalTaskRequests.add(externalTaskInfo);
  }

  public void addNewStartCommand(NewStartCommand newStartCommand) {
    newStartCommands.add(newStartCommand);
  }

  public void addContinuation(ContinueFlowElementTriggerDTO continueFlowElementTrigger) {
    continuations.add(continueFlowElementTrigger);
  }

  public void addTerminateCommand(UUID childProcessInstanceId) {
    newTerminateCommands.add(childProcessInstanceId);
  }

  public void addNewCorrelationSubcriptionMessageEvent(
      NewCorrelationSubscriptionMessageEventInfo messageEvent) {
    newCorrelationSubscriptionMessageEventInfos.add(messageEvent);
  }

  public void addTerminateCorrelationSubscriptionMessageEvent(
      TerminateCorrelationSubscriptionMessageEventInfo messageEvent) {
    terminateCorrelationSubscriptionMessageEventInfos.add(messageEvent);
  }

  public void addScheduledExternalTaskTriggerTimeout(
      ScheduledExternalTaskTriggerTimeoutInfo scheduledExternalTaskTriggerTimeoutInfo) {
    scheduledExternalTaskTriggerTimeouts.add(scheduledExternalTaskTriggerTimeoutInfo);
  }

  public void addNewScheduledContinuation(ScheduledContinuationInfo scheduledContinuationInfo) {
    scheduledContinuationInfos.add(scheduledContinuationInfo);
  }

  public void cancelSchedule(ScheduleKeyDTO scheduledKey) {
    cancelSchedules.add(scheduledKey);
  }

  public void addBubbleUpEvent(EventSignal eventSignal) {
    bubbleUpEvents.add(eventSignal);
  }
}
