package nl.qunit.bpmnmeister.scheduler.kafka.consumer;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.scheduler.RecurringCommand;
import nl.qunit.bpmnmeister.scheduler.model.recurring.RecurringCommandScheduler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Identifier("recurring-command-scheduler")
public class RecurringCommandConsumer extends GenericScheduleCommandConsumer<RecurringCommand> {

  @Inject RecurringCommandScheduler commandScheduler;

  @Incoming("recurring")
  @Override
  public void receive(ConsumerRecord<String, RecurringCommand> consumerRecord) {
    super.receive(consumerRecord);
  }

  String schedule(ConsumerRecord<String, RecurringCommand> consumerRecord) {
    return commandScheduler.schedule(consumerRecord.value());
  }

  void cancel(String id) {
    commandScheduler.cancel(id);
  }
}
