package nl.qunit.bpmnmeister.scheduler.kafka.consumer;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.model.scheduler.FixedRateCommand;
import nl.qunit.bpmnmeister.scheduler.model.command.CommandCompletionHandler;
import nl.qunit.bpmnmeister.scheduler.model.fixedrate.FixedRateCommandScheduler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Identifier("fixed-rate-command-scheduler")
public class FixedRateCommandConsumer extends GenericScheduleCommandConsumer<FixedRateCommand>
    implements CommandCompletionHandler {

  @Inject FixedRateCommandScheduler commandScheduler;

  @Incoming("fixed-rate")
  @Override
  public void receive(ConsumerRecord<String, FixedRateCommand> consumerRecord) {
    super.receive(consumerRecord);
  }

  String schedule(ConsumerRecord<String, FixedRateCommand> consumerRecord) {
    FixedRateCommand value = consumerRecord.value();
    return commandScheduler.schedule(value);
  }

  @Override
  void cancel(String schedulerId) {
    commandScheduler.cancel(schedulerId);
  }
}
