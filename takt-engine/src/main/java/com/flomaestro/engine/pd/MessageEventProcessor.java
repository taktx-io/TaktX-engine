package com.flomaestro.engine.pd;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.takt.dto.v_1_0_0.CancelCorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.CancelDefinitionMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageEventTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionMessageEventTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
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

  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;

  private ProcessorContext<Object, Object> context;
  private KeyValueStore<MessageEventKeyDTO, DefinitionMessageSubscriptions>
      definitionMessageSubscriptionStore;
  private KeyValueStore<MessageEventKeyDTO, CorrelationMessageSubscriptions>
      correlationMessageSubscriptionStore;
  private final Clock clock;

  public MessageEventProcessor(TenantNamespaceNameWrapper tenantNamespaceNameWrapper, Clock clock) {
    this.tenantNamespaceNameWrapper = tenantNamespaceNameWrapper;
    this.clock = clock;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.definitionMessageSubscriptionStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(
                Stores.DEFINITION_MESSAGE_SUBSCRIPTION.getStorename()));
    this.correlationMessageSubscriptionStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(
                Stores.CORRELATION_MESSAGE_SUBSCRIPTION.getStorename()));
  }

  @Override
  public void process(Record<MessageEventKeyDTO, MessageEventDTO> messageEventRecord) {
    if (messageEventRecord.value()
        instanceof DefinitionMessageSubscriptionDTO startEventMessageSubscription) {
      storeDefinitionMessageSubscription(messageEventRecord.key(), startEventMessageSubscription);
    } else if (messageEventRecord.value()
        instanceof CorrelationMessageSubscriptionDTO correlatingMessageSubscription) {
      storeCorrelationMessageSubscription(messageEventRecord.key(), correlatingMessageSubscription);
    } else if (messageEventRecord.value()
        instanceof CancelDefinitionMessageSubscriptionDTO cancelDefinitionMessageSubscription) {
      cancelDefinitionMessageSubscription(
          messageEventRecord.key(), cancelDefinitionMessageSubscription);
    } else if (messageEventRecord.value()
        instanceof CancelCorrelationMessageSubscriptionDTO cancelCorrelatingMessageSubscription) {
      cancelCorrelationMessageSubscription(
          messageEventRecord.key(), cancelCorrelatingMessageSubscription);
    } else if (messageEventRecord.value()
        instanceof DefinitionMessageEventTriggerDTO messageEvent) {
      processDefinitionMessageEventTrigger(messageEventRecord.key(), messageEvent);
    } else if (messageEventRecord.value()
        instanceof CorrelationMessageEventTriggerDTO messageEvent) {
      processCorrelationMessageEventTrigger(messageEventRecord.key(), messageEvent);
    } else {
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
      this.definitionMessageSubscriptionStore.put(key, removed);
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
      this.correlationMessageSubscriptionStore.put(messageEventKey, removed);
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
                  ContinueFlowElementTriggerDTO flowElementTrigger =
                      new ContinueFlowElementTriggerDTO(
                          processInstanceKey,
                          subscription.getElementInstanceIdPath(),
                          null,
                          messageEvent.getVariables());

                  context.forward(
                      new Record<>(processInstanceKey, flowElementTrigger, clock.millis()));
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
                          List.of(value.getElementId()),
                          List.of(),
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
