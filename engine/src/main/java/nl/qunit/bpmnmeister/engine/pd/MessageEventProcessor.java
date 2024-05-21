package nl.qunit.bpmnmeister.engine.pd;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.StartCommand;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class MessageEventProcessor
    implements Processor<MessageEventKey, MessageEvent, String, StartCommand> {

  private ProcessorContext<String, StartCommand> context;
  private KeyValueStore<MessageEventKey, MessageSubscription> messageSubscriptionStore;

  @Override
  public void init(ProcessorContext<String, StartCommand> context) {
    this.context = context;
    this.messageSubscriptionStore = context.getStateStore(Stores.MESSAGE_SUBSCRIPTION_STORE_NAME);
  }

  @Override
  public void process(Record<MessageEventKey, MessageEvent> record) {
    if (record.value() instanceof MessageSubscription messageSubscription) {
      log.info("New message subscription: {}", messageSubscription);
      this.messageSubscriptionStore.put(record.key(), messageSubscription);
    } else if (record.value() == null) {
      log.info("Cancel message subscription: {}", record.key());
      this.messageSubscriptionStore.delete(record.key());
    } else if (record.value() instanceof MessageEventImpl messageEvent) {
      log.info("Processing message: {}", messageEvent);
      MessageSubscription messageSubscription = this.messageSubscriptionStore.get(record.key());
      if (messageSubscription != null) {
        ProcessDefinitionKey processDefinitionKey = messageSubscription.key();
        StartCommand startCommand =
            new StartCommand(
                ProcessInstanceKey.NONE,
                messageSubscription.elementId(),
                Constants.NONE,
                processDefinitionKey.getProcessDefinitionId(),
                messageEvent.getVariables());
        log.info("Start command due to message {} received: {}", messageEvent, startCommand);

        context.forward(
            new Record<>(
                processDefinitionKey.getProcessDefinitionId(),
                startCommand,
                Instant.now().toEpochMilli()));
      }
    } else {
      throw new IllegalArgumentException("Unknown message event type");
    }
  }
}
