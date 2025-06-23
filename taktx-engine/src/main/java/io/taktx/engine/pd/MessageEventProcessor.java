/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd;

import io.taktx.dto.CancelCorrelationMessageSubscriptionDTO;
import io.taktx.dto.CancelDefinitionMessageSubscriptionDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.DefinitionMessageSubscriptionDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.engine.config.TaktConfiguration;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class MessageEventProcessor
    implements Processor<MessageEventKeyDTO, MessageEventDTO, Object, Object> {

  private final TaktConfiguration taktConfiguration;

  private ProcessorContext<Object, Object> context;
  private KeyValueStore<MessageEventKeyDTO, DefinitionMessageSubscriptions>
      definitionMessageSubscriptionStore;
  private KeyValueStore<MessageEventKeyDTO, CorrelationMessageSubscriptions>
      correlationMessageSubscriptionStore;
  private final Clock clock;

  public MessageEventProcessor(TaktConfiguration taktConfiguration, Clock clock) {
    this.taktConfiguration = taktConfiguration;
    this.clock = clock;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.definitionMessageSubscriptionStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.DEFINITION_MESSAGE_SUBSCRIPTION.getStorename()));
    this.correlationMessageSubscriptionStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.CORRELATION_MESSAGE_SUBSCRIPTION.getStorename()));
  }

  @Override
  public void process(Record<MessageEventKeyDTO, MessageEventDTO> messageEventRecord) {
    switch (messageEventRecord.value()) {
      case DefinitionMessageSubscriptionDTO startEventMessageSubscription ->
          storeDefinitionMessageSubscription(
              messageEventRecord.key(), startEventMessageSubscription);
      case CorrelationMessageSubscriptionDTO correlatingMessageSubscription ->
          storeCorrelationMessageSubscription(
              messageEventRecord.key(), correlatingMessageSubscription);
      case CancelDefinitionMessageSubscriptionDTO cancelDefinitionMessageSubscription ->
          cancelDefinitionMessageSubscription(
              messageEventRecord.key(), cancelDefinitionMessageSubscription);
      case CancelCorrelationMessageSubscriptionDTO cancelCorrelatingMessageSubscription ->
          cancelCorrelationMessageSubscription(
              messageEventRecord.key(), cancelCorrelatingMessageSubscription);
      case DefinitionMessageEventTriggerDTO messageEvent ->
          processDefinitionMessageEventTrigger(messageEventRecord.key(), messageEvent);
      case CorrelationMessageEventTriggerDTO messageEvent ->
          processCorrelationMessageEventTrigger(messageEventRecord.key(), messageEvent);
      default ->
          throw new IllegalArgumentException(
              "Unknown message event type" + messageEventRecord.value().getClass());
    }
  }

  private void cancelDefinitionMessageSubscription(
      MessageEventKeyDTO key,
      CancelDefinitionMessageSubscriptionDTO cancelDefinitionMessageSubscription) {
    DefinitionMessageSubscriptions messageSubscriptions =
        this.definitionMessageSubscriptionStore.get(key);
    if (messageSubscriptions != null) {
      DefinitionMessageSubscriptions removed =
          messageSubscriptions.remove(cancelDefinitionMessageSubscription);
      if (removed.getDefinitions().isEmpty()) {
        this.definitionMessageSubscriptionStore.put(key, null);
      } else {
        this.definitionMessageSubscriptionStore.put(key, removed);
      }
    }
  }

  private void cancelCorrelationMessageSubscription(
      MessageEventKeyDTO messageEventKey,
      CancelCorrelationMessageSubscriptionDTO cancelCorrelatingMessageSubscription) {
    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions != null) {
      CorrelationMessageSubscriptions removed =
          messageSubscriptions.remove(cancelCorrelatingMessageSubscription.getCorrelationKey());
      if (removed.getInstances().isEmpty()) {
        this.correlationMessageSubscriptionStore.put(messageEventKey, null);
      } else {
        this.correlationMessageSubscriptionStore.put(messageEventKey, removed);
      }
    }
  }

  private void processCorrelationMessageEventTrigger(
      MessageEventKeyDTO messageEventKey, CorrelationMessageEventTriggerDTO messageEvent) {

    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions != null) {
      messageSubscriptions
          .getInstances()
          .values()
          .forEach(
              subscription -> {
                if (subscription.getCorrelationKey().equals(messageEvent.getCorrelationKey())) {
                  UUID processInstanceKey = subscription.getProcessInstanceKey();
                  if (subscription.getElementId() == null) {
                    ContinueFlowElementTriggerDTO flowElementTrigger =
                        new ContinueFlowElementTriggerDTO(
                            processInstanceKey,
                            subscription.getElementInstanceIdPath(),
                            null,
                            messageEvent.getVariables());

                    context.forward(
                        new Record<>(processInstanceKey, flowElementTrigger, clock.millis()));
                  } else {
                    StartFlowElementTriggerDTO flowElementTrigger =
                        new StartFlowElementTriggerDTO(
                            processInstanceKey,
                            subscription.getElementInstanceIdPath() == null
                                ? List.of()
                                : subscription.getElementInstanceIdPath(),
                            subscription.getElementId(),
                            messageEvent.getVariables());
                    context.forward(
                        new Record<>(processInstanceKey, flowElementTrigger, clock.millis()));
                  }
                }
              });
    }
  }

  private void processDefinitionMessageEventTrigger(
      MessageEventKeyDTO messageEventKey, DefinitionMessageEventTriggerDTO messageEvent) {
    DefinitionMessageSubscriptions messageSubscription =
        this.definitionMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscription != null) {
      messageSubscription
          .getDefinitions()
          .values()
          .forEach(
              value -> {
                if (value.getMessageName().equals(messageEvent.getMessageName())) {
                  ProcessDefinitionKey processDefinitionKey = value.getProcessDefinitionKey();
                  UUID processInstanceKey = UUID.randomUUID();
                  StartCommandDTO startCommand =
                      new StartCommandDTO(
                          processInstanceKey,
                          value.getElementId(),
                          List.of(),
                          new ProcessDefinitionKey(processDefinitionKey.getProcessDefinitionId()),
                          messageEvent.getVariables());

                  context.forward(new Record<>(processInstanceKey, startCommand, clock.millis()));
                }
              });
    }
  }

  private void storeCorrelationMessageSubscription(
      MessageEventKeyDTO messageEventKey,
      CorrelationMessageSubscriptionDTO correlatingMessageSubscription) {
    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions == null) {
      messageSubscriptions = new CorrelationMessageSubscriptions(new HashMap<>());
    }
    messageSubscriptions = messageSubscriptions.update(correlatingMessageSubscription);
    this.correlationMessageSubscriptionStore.put(messageEventKey, messageSubscriptions);
  }

  private void storeDefinitionMessageSubscription(
      MessageEventKeyDTO messageEventKey,
      DefinitionMessageSubscriptionDTO startEventMessageSubscription) {
    DefinitionMessageSubscriptions messageSubscriptions =
        this.definitionMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions == null) {
      messageSubscriptions = new DefinitionMessageSubscriptions(new HashMap<>());
    }
    messageSubscriptions = messageSubscriptions.update(startEventMessageSubscription);
    this.definitionMessageSubscriptionStore.put(messageEventKey, messageSubscriptions);
  }
}
