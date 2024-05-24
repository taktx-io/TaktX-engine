package nl.qunit.bpmnmeister.engine.pd;

import java.time.Instant;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.StartCommand;
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
      storeDefinitionMessageSubscription(messageEventRecord, startEventMessageSubscription);
    } else if (messageEventRecord.value()
        instanceof CorrelationMessageSubscription correlatingMessageSubscription) {
      storeCorrelationMessageSubscription(messageEventRecord, correlatingMessageSubscription);
    } else if (messageEventRecord.value() instanceof DefinitionMessageEventTrigger messageEvent) {
      processDefinitionMessageEventTrigger(messageEventRecord, messageEvent);
    } else if (messageEventRecord.value() instanceof CorrelationMessageEventTrigger messageEvent) {
      processCorrelationMessageEventTrigger(messageEventRecord, messageEvent);
    } else {
      throw new IllegalArgumentException(
          "Unknown message event type" + messageEventRecord.value().getClass());
    }
  }

  private void processCorrelationMessageEventTrigger(
      Record<MessageEventKey, MessageEvent> eventTriggerRecord,
      CorrelationMessageEventTrigger messageEvent) {
    log.info("New correlation message event trigger: {}", messageEvent);

    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(eventTriggerRecord.key());
    if (messageSubscriptions != null) {
      messageSubscriptions
          .getInstances()
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
                  // No more use for the scheduled message, remove it from the store
                  CorrelationMessageSubscriptions newMessageSubscriptions =
                      messageSubscriptions.remove(subscription);
                  if (newMessageSubscriptions.getInstances().isEmpty()) {
                    this.correlationMessageSubscriptionStore.delete(eventTriggerRecord.key());
                  } else {
                    this.correlationMessageSubscriptionStore.put(
                        eventTriggerRecord.key(), newMessageSubscriptions);
                  }
                }
              });
    }
  }

  private void processDefinitionMessageEventTrigger(
      Record<MessageEventKey, MessageEvent> eventRecord,
      DefinitionMessageEventTrigger messageEvent) {
    log.info("New definition message event trigger: {}", messageEvent);

    DefinitionMessageSubscriptions messageSubscription =
        this.definitionMessageSubscriptionStore.get(eventRecord.key());
    if (messageSubscription != null) {
      messageSubscription
          .getDefinitions()
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
      Record<MessageEventKey, MessageEvent> subScriptionRecord,
      CorrelationMessageSubscription correlatingMessageSubscription) {
    log.info("New correlating message subscription: {}", correlatingMessageSubscription);
    CorrelationMessageSubscriptions messageSubscriptions =
        this.correlationMessageSubscriptionStore.get(subScriptionRecord.key());
    if (messageSubscriptions == null) {
      messageSubscriptions = new CorrelationMessageSubscriptions(Set.of());
    }
    messageSubscriptions = messageSubscriptions.update(correlatingMessageSubscription);
    log.info(
        "new message correlating subscriptions for message key {}: {}",
        subScriptionRecord.key(),
        messageSubscriptions);
    this.correlationMessageSubscriptionStore.put(subScriptionRecord.key(), messageSubscriptions);
  }

  private void storeDefinitionMessageSubscription(
      Record<MessageEventKey, MessageEvent> subscriptionRecord,
      DefinitionMessageSubscription startEventMessageSubscription) {
    log.info("New definition message subscription: {}", startEventMessageSubscription);
    DefinitionMessageSubscriptions messageSubscriptions =
        this.definitionMessageSubscriptionStore.get(subscriptionRecord.key());
    if (messageSubscriptions == null) {
      messageSubscriptions = new DefinitionMessageSubscriptions(Set.of());
    }
    messageSubscriptions = messageSubscriptions.update(startEventMessageSubscription);
    this.definitionMessageSubscriptionStore.put(subscriptionRecord.key(), messageSubscriptions);
  }
}
