package nl.qunit.bpmnmeister.scheduler.model.command;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.model.scheduler.ScheduleCommand;
import nl.qunit.bpmnmeister.scheduler.kafka.producer.ReplyProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

@ApplicationScoped
public class CommandHandler {

  ReplyProducer replyProducer;

  /**
   * Send the payload/command which was defined in the ScheduleCommand to the topic which was
   * defined in the ScheduleCommand.
   */
  public void sendCommand(ScheduleCommand command) {
    command
        .triggers()
        .forEach(trigger -> replyProducer.send(new ProducerRecord<>(command.id(), trigger)));
  }
}
