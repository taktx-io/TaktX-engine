package nl.qunit.bpmnmeister.scheduler.model.command;

import nl.qunit.bpmnmeister.scheduler.ScheduleCommand;
import nl.qunit.bpmnmeister.scheduler.kafka.producer.ReplyProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public abstract class AbstractCommandHandler<C extends ScheduleCommand> {

  final ReplyProducer replyProducer;

  protected AbstractCommandHandler(ReplyProducer replyProducer) {
    this.replyProducer = replyProducer;
  }

  /**
   * Send the payload/command which was defined in the ScheduleCommand to the topic which was
   * defined in the ScheduleCommand.
   */
  public void sendCommand(C command) {
    command
        .triggers()
        .forEach(
            trigger ->
                replyProducer.send(new ProducerRecord<>("trigger-topic", command.scheduleKey(), trigger)));
  }

  public abstract void run(C command);
}
