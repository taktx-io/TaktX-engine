package nl.qunit.bpmnmeister.scheduler.kafka.consumer;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.scheduler.model.command.CommandCompletionHandler;
import nl.qunit.bpmnmeister.scheduler.OneTimeCommand;
import nl.qunit.bpmnmeister.scheduler.model.one_time.OneTimeCommandScheduler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Identifier("one-time-command-scheduler")
@Default
public class OneTimeCommandConsumer extends GenericScheduleCommandConsumer<OneTimeCommand>
    implements CommandCompletionHandler {

  @Inject OneTimeCommandScheduler commandScheduler;

  @Incoming("one-time")
  @Override
  public void receive(ConsumerRecord<String, OneTimeCommand> consumerRecord) {
    super.receive(consumerRecord);
  }

  String schedule(ConsumerRecord<String, OneTimeCommand> consumerRecord) {
    return commandScheduler.schedule(consumerRecord.value());
  }

  void cancel(String schedulerId) {
    commandScheduler.cancel(schedulerId);
  }
}
