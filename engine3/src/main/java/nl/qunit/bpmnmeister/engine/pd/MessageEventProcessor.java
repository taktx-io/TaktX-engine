package nl.qunit.bpmnmeister.engine.pd;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.CancelCorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.CancelDefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.CorrelationMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.DefinitionMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.DefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class MessageEventProcessor
    implements Processor<MessageEventKey, MessageEvent, Object, Object> {

  private ProcessorContext<Object, Object> context;
  private KeyValueStore<MessageEventKey, DefinitionMessageSubscriptions>
      definitionMessageSubscriptionStore;
  private KeyValueStore<MessageEventKey, CorrelationMessageSubscriptions>
      correlationMessageSubscriptionStore;

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.definitionMessageSubscriptionStore =
        context.getStateStore(Stores.DEFINITION_MESSAGE_SUBSCRIPTION_STORE_NAME);
    this.correlationMessageSubscriptionStore =
        context.getStateStore(Stores.CORRELATION_MESSAGE_SUBSCRIPTION_STORE_NAME);
  }

  @Override
  public void process(Record<MessageEventKey, MessageEvent> messageEventRecord) {
    if (messageEventRecord.value()
        instanceof DefinitionMessageSubscription startEventMessageSubscription) {
      storeDefinitionMessageSubscription(messageEventRecord.key(), startEventMessageSubscription);
    } else if (messageEventRecord.value()
        instanceof CorrelationMessageSubscription correlatingMessageSubscription) {
      storeCorrelationMessageSubscription(messageEventRecord.key(), correlatingMessageSubscription);
    } else if (messageEventRecord.value()
        instanceof CancelDefinitionMessageSubscription cancelDefinitionMessageSubscription) {
      cancelDefinitionMessageSubscription(
          messageEventRecord.key(), cancelDefinitionMessageSubscription);
    } else if (messageEventRecord.value()
        instanceof CancelCorrelationMessageSubscription cancelCorrelatingMessageSubscription) {
      cancelCorrelationMessageSubscription(
          messageEventRecord.key(), cancelCorrelatingMessageSubscription);
    } else if (messageEventRecord.value() instanceof DefinitionMessageEventTrigger messageEvent) {
      processDefinitionMessageEventTrigger(messageEventRecord.key(), messageEvent);
    } else if (messageEventRecord.value() instanceof CorrelationMessageEventTrigger messageEvent) {
      processCorrelationMessageEventTrigger(messageEventRecord.key(), messageEvent);
    } else {
      throw new IllegalArgumentException(
          "Unknown message event type" + messageEventRecord.value().getClass());
    }
  }

  private void cancelDefinitionMessageSubscription(
      MessageEventKey key,
      CancelDefinitionMessageSubscription cancelDefinitionMessageSubscription) {
    DefinitionMessageSubscriptions messageSubscriptions =
        this.definitionMessageSubscriptionStore.get(key);
    if (messageSubscriptions != null) {
      DefinitionMessageSubscriptions removed =
          messageSubscriptions.remove(cancelDefinitionMessageSubscription);
      this.definitionMessageSubscriptionStore.put(key, removed);
    }
  }

  private void cancelCorrelationMessageSubscription(
      MessageEventKey messageEventKey,
      CancelCorrelationMessageSubscription cancelCorrelatingMessageSubscription) {
    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions != null) {
      CorrelationMessageSubscriptions removed =
          messageSubscriptions.remove(cancelCorrelatingMessageSubscription.getCorrelationKey());
      this.correlationMessageSubscriptionStore.put(messageEventKey, removed);
    }
  }

  private void processCorrelationMessageEventTrigger(
      MessageEventKey messageEventKey, CorrelationMessageEventTrigger messageEvent) {

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
                  ContinueFlowElementTrigger2 flowElementTrigger =
                      new ContinueFlowElementTrigger2(
                          processInstanceKey,
                          subscription.getElementIdPath(),
                          subscription.getElementInstanceIdPath(),
                          Constants.NONE,
                          messageEvent.getVariables());

                  context.forward(
                      new Record<>(
                          processInstanceKey, flowElementTrigger, Instant.now().toEpochMilli()));
                }
              });
    }
  }

  private void processDefinitionMessageEventTrigger(
      MessageEventKey messageEventKey, DefinitionMessageEventTrigger messageEvent) {
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
                  StartCommand startCommand =
                      new StartCommand(
                          Constants.NONE_UUID,
                          Constants.NONE_UUID,
                          value.getElementId(),
                          List.of(),
                          List.of(),
                          processDefinitionKey.getProcessDefinitionId(),
                          messageEvent.getVariables());

                  context.forward(
                      new Record<>(
                          processDefinitionKey.getProcessDefinitionId(),
                          startCommand,
                          Instant.now().toEpochMilli()));
                }
              });
    }
  }

  private void storeCorrelationMessageSubscription(
      MessageEventKey messageEventKey,
      CorrelationMessageSubscription correlatingMessageSubscription) {
    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions == null) {
      messageSubscriptions = new CorrelationMessageSubscriptions(new HashMap<>());
    }
    messageSubscriptions = messageSubscriptions.update(correlatingMessageSubscription);
    this.correlationMessageSubscriptionStore.put(messageEventKey, messageSubscriptions);
  }

  private void storeDefinitionMessageSubscription(
      MessageEventKey messageEventKey,
      DefinitionMessageSubscription startEventMessageSubscription) {
    DefinitionMessageSubscriptions messageSubscriptions =
        this.definitionMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions == null) {
      messageSubscriptions = new DefinitionMessageSubscriptions(new HashMap<>());
    }
    messageSubscriptions = messageSubscriptions.update(startEventMessageSubscription);
    this.definitionMessageSubscriptionStore.put(messageEventKey, messageSubscriptions);
  }
}
