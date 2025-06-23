/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd;

import io.taktx.dto.CancelDefinitionMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageSubscriptionDTO;
import io.taktx.dto.DefinitionScheduleKeyDTO;
import io.taktx.dto.MessageDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.ProcessDefinitionActivationDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessDefinitionStateEnum;
import io.taktx.dto.SchedulableMessageDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartEventDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

public class ProcessDefinitionActivationProcessor {

  private final MessageSchedulerFactory messageSchedulerFactory;
  private final ProcessorContext<Object, Object> context;
  private final Clock clock;
  private final KeyValueStore<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>>
      processDefinitionStore;

  public ProcessDefinitionActivationProcessor(
      TaktConfiguration taktConfiguration,
      MessageSchedulerFactory messageSchedulerFactory,
      ProcessorContext<Object, Object> context,
      Clock clock) {
    this.messageSchedulerFactory = messageSchedulerFactory;
    this.context = context;
    this.clock = clock;
    this.processDefinitionStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()));
  }

  public void process(ProcessDefinitionActivationDTO processActivationRecord) {
    ValueAndTimestamp<ProcessDefinitionDTO> valueAndTimestamp =
        processDefinitionStore.get(processActivationRecord.getProcessDefinitionKey());
    if (processActivationRecord.getState() == ProcessDefinitionStateEnum.ACTIVE) {
      activate(valueAndTimestamp.value());
    } else if (processActivationRecord.getState() == ProcessDefinitionStateEnum.INACTIVE) {
      deactivate(valueAndTimestamp.value());
    }
  }

  public void activate(ProcessDefinitionDTO processDefinition) {
    // Deactivate all other versions of the process definition
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(processDefinition);
    ProcessDefinitionKey startKey =
        new ProcessDefinitionKey(processDefinitionKey.getProcessDefinitionId(), 1);
    ProcessDefinitionKey endKey =
        new ProcessDefinitionKey(processDefinitionKey.getProcessDefinitionId(), Integer.MAX_VALUE);

    try (KeyValueIterator<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>> range =
        processDefinitionStore.range(startKey, endKey)) {
      range.forEachRemaining(
          entry -> {
            if (!entry.key.equals(processDefinitionKey)) {
              deactivate(entry.value.value());
            }
          });
    }

    ProcessDefinitionDTO activatedDefinition =
        new ProcessDefinitionDTO(
            processDefinition.getDefinitions(),
            processDefinition.getVersion(),
            ProcessDefinitionStateEnum.ACTIVE);

    processDefinition
        .getDefinitions()
        .getRootProcess()
        .getFlowElements()
        .getStartEvents()
        .forEach(
            startEvent -> {
              scheduleStartCommands(processDefinitionKey, startEvent);
              subscribetoStartMessageEvents(processDefinitionKey, startEvent, processDefinition);
            });

    context.forward(new Record<>(processDefinitionKey, activatedDefinition, clock.millis()));
    ProcessDefinitionKey latestKey =
        new ProcessDefinitionKey(processDefinitionKey.getProcessDefinitionId(), -1);
    context.forward(new Record<>(latestKey, activatedDefinition, clock.millis()));
  }

  public void deactivate(ProcessDefinitionDTO processDefinition) {
    if (processDefinition.getState() == ProcessDefinitionStateEnum.INACTIVE) {
      return;
    }

    ProcessDefinitionDTO deactivatedProcessDefinition =
        new ProcessDefinitionDTO(
            processDefinition.getDefinitions(),
            processDefinition.getVersion(),
            ProcessDefinitionStateEnum.INACTIVE);

    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(processDefinition);
    deactivatedProcessDefinition
        .getDefinitions()
        .getRootProcess()
        .getFlowElements()
        .getStartEvents()
        .forEach(
            startEvent -> {
              cancelScheduledStartCommands(processDefinitionKey, startEvent);
              unsubscribeFromStartMessageEvents(startEvent, deactivatedProcessDefinition);
            });

    context.forward(
        new Record<>(processDefinitionKey, deactivatedProcessDefinition, clock.millis()));
  }

  private void subscribetoStartMessageEvents(
      ProcessDefinitionKey processDefinitionKey,
      StartEventDTO startEvent,
      ProcessDefinitionDTO processDefinition) {
    startEvent
        .getMessageventDefinitions()
        .forEach(
            messageStartEventDefinition -> {
              String messageRef = messageStartEventDefinition.getMessageRef();
              MessageDTO message = processDefinition.getDefinitions().getMessages().get(messageRef);
              String messageName = message.getName();
              MessageEventDTO messageSubscription =
                  new DefinitionMessageSubscriptionDTO(
                      processDefinitionKey, startEvent.getId(), messageName);

              context.forward(
                  new Record<>(
                      messageSubscription.toMessageEventKey(),
                      messageSubscription,
                      clock.millis()));
            });
  }

  private void unsubscribeFromStartMessageEvents(
      StartEventDTO startEvent, ProcessDefinitionDTO processDefinition) {
    startEvent
        .getMessageventDefinitions()
        .forEach(
            messageStartEventDefinition -> {
              String messageRef = messageStartEventDefinition.getMessageRef();
              MessageDTO message = processDefinition.getDefinitions().getMessages().get(messageRef);
              String messageName = message.getName();
              MessageEventDTO cancelSubscription =
                  new CancelDefinitionMessageSubscriptionDTO(messageName);
              context.forward(
                  new Record<>(
                      cancelSubscription.toMessageEventKey(), cancelSubscription, clock.millis()));
            });
  }

  private void cancelScheduledStartCommands(
      ProcessDefinitionKey processDefinitionKey, StartEventDTO startEvent) {
    // TODO ????
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              //              DefinitionScheduleKeyDTO scheduleKey =
              //                  new DefinitionScheduleKeyDTO(processDefinitionKey,
              // startEvent.getId());
              //              context.forward(new Record<>(scheduleKey, null, clock.millis()));
            });
  }

  private void scheduleStartCommands(
      ProcessDefinitionKey processDefinitionKey, StartEventDTO startEvent) {
    long now = clock.millis();
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              MessageScheduleDTO schedule =
                  messageSchedulerFactory.schedule(
                      timerEventDefinition,
                      now,
                      getStartCommand(processDefinitionKey.getProcessDefinitionId(), startEvent),
                      new VariableScope(null, null, null, null));
              TimeBucket timeBucket =
                  TimeBucket.ofMillis(
                      schedule.getNextExecutionTime(schedule.getInstantiationTime())
                          - schedule.getInstantiationTime());
              if (timeBucket != null) {
                DefinitionScheduleKeyDTO scheduleKey =
                    new DefinitionScheduleKeyDTO(
                        processDefinitionKey, startEvent.getId(), timeBucket);
                context.forward(new Record<>(scheduleKey, schedule, now));
              }
            });
  }

  private static SchedulableMessageDTO getStartCommand(
      String processDefinitionId, StartEventDTO startEvent) {
    return new StartCommandDTO(
        null,
        startEvent.getParentId(),
        null,
        new ProcessDefinitionKey(processDefinitionId),
        VariablesDTO.empty());
  }
}
