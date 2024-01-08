package nl.qunit.bpmnmeister.scheduler.model.one_time;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import nl.qunit.bpmnmeister.scheduler.kafka.producer.OneTimeCommandDeletionProducer;
import nl.qunit.bpmnmeister.scheduler.kafka.producer.ReplyProducer;
import nl.qunit.bpmnmeister.scheduler.model.command.AbstractCommandHandler;
import nl.qunit.bpmnmeister.scheduler.model.command.CommandCompletionHandler;
import nl.qunit.bpmnmeister.scheduler.model.command.OneTimeCommand;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

@Dependent
@Unremovable
public class OneTimeCommandHandler extends AbstractCommandHandler<OneTimeCommand> {

  private final CommandCompletionHandler commandCompletionHandler;
  private final OneTimeCommandDeletionProducer commandDeletionProducer;
  private final Logger log = org.slf4j.LoggerFactory.getLogger(getClass());

  private final String oneTimeSchedulerTopic;

  protected OneTimeCommandHandler(
      ReplyProducer replyProducer,
      CommandCompletionHandler commandCompletionHandler,
      OneTimeCommandDeletionProducer commandDeletionProducer,
      @ConfigProperty(name = "mp.messaging.incoming.one-time.topic") String oneTimeSchedulerTopic) {
    super(replyProducer);
    this.commandCompletionHandler = commandCompletionHandler;
    this.commandDeletionProducer = commandDeletionProducer;
    this.oneTimeSchedulerTopic = oneTimeSchedulerTopic;
  }

  @Override
  public void run(OneTimeCommand command) {
    try {
      sendCommand(command);

      deleteScheduleCommand(command);

      this.commandCompletionHandler.commandCompleted(command);
    } catch (Exception exception) {
      log.error("Error while trying to send command. Will not delete ScheduleCommand", exception);
    }
  }

  /** Send a tombstone message deleting the ScheduleCommand from the topic. */
  private void deleteScheduleCommand(OneTimeCommand command) {
    ProducerRecord<String, Void> scheduleCommandDeletionProducerRecord =
        new ProducerRecord<>(oneTimeSchedulerTopic, command.id(), null);
    log.info("Deleting ScheduleCommand: {}", command.id());
    this.commandDeletionProducer.send(scheduleCommandDeletionProducerRecord);
  }
}
