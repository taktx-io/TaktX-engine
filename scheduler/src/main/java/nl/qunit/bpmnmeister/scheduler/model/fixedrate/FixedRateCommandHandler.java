package nl.qunit.bpmnmeister.scheduler.model.fixedrate;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import nl.qunit.bpmnmeister.model.scheduler.FixedRateCommand;
import nl.qunit.bpmnmeister.scheduler.kafka.producer.FixedRateCommandDeletionProducer;
import nl.qunit.bpmnmeister.scheduler.kafka.producer.FixedRateCommandUpdateProducer;
import nl.qunit.bpmnmeister.scheduler.kafka.producer.ReplyProducer;
import nl.qunit.bpmnmeister.scheduler.model.command.AbstractCommandHandler;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Dependent
@Unremovable
public class FixedRateCommandHandler extends AbstractCommandHandler<FixedRateCommand> {

  private final FixedRateCommandDeletionProducer commandDeletionProducer;
  private final FixedRateCommandUpdateProducer fixedRateCommandUpdateProducer;
  private final String fixedRateSchedulerTopic;

  private Map<UUID, Integer> repeatedCntMap = new HashMap<>();

  protected FixedRateCommandHandler(
      ReplyProducer replyProducer,
      FixedRateCommandDeletionProducer commandDeletionProducer,
      FixedRateCommandUpdateProducer fixedRateCommandUpdateProducer,
      @ConfigProperty(name = "mp.messaging.incoming.fixed-rate.topic")
          String fixedRateSchedulerTopic) {
    super(replyProducer);
    this.commandDeletionProducer = commandDeletionProducer;
    this.fixedRateCommandUpdateProducer = fixedRateCommandUpdateProducer;
    this.fixedRateSchedulerTopic = fixedRateSchedulerTopic;
  }

  @Override
  public void run(FixedRateCommand command) {
    int repeatedCnt = repeatedCntMap.getOrDefault(command.id(), command.repeatedCnt());

    FixedRateCommand updatedCommand =
        new FixedRateCommand(
            command.id(),
            command.triggers(),
            command.period(),
            command.repetitions(),
            repeatedCnt + 1);
    if (updatedCommand.repeatedCnt() <= command.repetitions()) {
      sendCommand(updatedCommand);
      updateScheduleCommand(updatedCommand);
    } else {
      deleteScheduleCommand(command);
    }
  }

  /** Send a tombstone message deleting the ScheduleCommand from the topic. */
  private void deleteScheduleCommand(FixedRateCommand command) {
    ProducerRecord<UUID, Void> scheduleCommandDeletionProducerRecord =
        new ProducerRecord<>(fixedRateSchedulerTopic, command.id(), null);
    this.commandDeletionProducer.send(scheduleCommandDeletionProducerRecord);
  }

  /** Send a update message updating the repetitioncount. */
  private void updateScheduleCommand(FixedRateCommand command) {
    this.repeatedCntMap.put(command.id(), command.repeatedCnt());
    ProducerRecord<UUID, FixedRateCommand> scheduleCommandDeletionProducerRecord =
        new ProducerRecord<>(fixedRateSchedulerTopic, command.id(), command);
    this.fixedRateCommandUpdateProducer.send(scheduleCommandDeletionProducerRecord);
  }
}
