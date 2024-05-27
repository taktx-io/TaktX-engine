package nl.qunit.bpmnmeister.engine.pd;

import java.time.Instant;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.CancelCorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.CancelDefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.CorrelationMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.DefinitionMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.DefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
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
    log.info("Cancel definition message subscription: {}", cancelDefinitionMessageSubscription);
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
    log.info("Cancel correlating message subscription: {}", cancelCorrelatingMessageSubscription);
    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions != null) {
      CorrelationMessageSubscriptions removed = messageSubscriptions.remove(messageEventKey);
      this.correlationMessageSubscriptionStore.put(messageEventKey, removed);
    }
  }

  private void processCorrelationMessageEventTrigger(
      MessageEventKey messageEventKey, CorrelationMessageEventTrigger messageEvent) {
    log.info("New correlation message event trigger: {}", messageEvent);

    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions != null) {
      messageSubscriptions
          .getInstances()
          .values()
          .forEach(
              subscription -> {
                if (subscription.getCorrelationKey().equals(messageEvent.getCorrelationKey())) {
                  ProcessInstanceKey processInstanceKey = subscription.getProcessInstanceKey();
                  FlowElementTrigger flowElementTrigger =
                      new FlowElementTrigger(
                          processInstanceKey,
                          subscription.getElementId(),
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
    log.info("New definition message event trigger: {}", messageEvent);

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
                          ProcessInstanceKey.NONE,
                          value.getElementId(),
                          Constants.NONE,
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
    log.info("New correlating message subscription: {}", correlatingMessageSubscription);
    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions == null) {
      messageSubscriptions = new CorrelationMessageSubscriptions(new HashMap<>());
    }
    messageSubscriptions = messageSubscriptions.update(correlatingMessageSubscription);
    log.info(
        "new message correlating subscriptions for message key {}: {}",
        messageEventKey,
        messageSubscriptions);
    this.correlationMessageSubscriptionStore.put(messageEventKey, messageSubscriptions);
  }

  private void storeDefinitionMessageSubscription(
      MessageEventKey messageEventKey,
      DefinitionMessageSubscription startEventMessageSubscription) {
    log.info("New definition message subscription: {}", startEventMessageSubscription);
    DefinitionMessageSubscriptions messageSubscriptions =
        this.definitionMessageSubscriptionStore.get(messageEventKey);
    if (messageSubscriptions == null) {
      messageSubscriptions = new DefinitionMessageSubscriptions(new HashMap<>());
    }
    messageSubscriptions = messageSubscriptions.update(startEventMessageSubscription);
    this.definitionMessageSubscriptionStore.put(messageEventKey, messageSubscriptions);
  }
}
