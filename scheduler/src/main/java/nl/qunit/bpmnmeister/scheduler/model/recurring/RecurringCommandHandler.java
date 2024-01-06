package nl.qunit.bpmnmeister.scheduler.model.recurring;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import nl.qunit.bpmnmeister.model.scheduler.RecurringCommand;
import nl.qunit.bpmnmeister.scheduler.kafka.producer.ReplyProducer;
import nl.qunit.bpmnmeister.scheduler.model.command.AbstractCommandHandler;

@Dependent
@Unremovable
public class RecurringCommandHandler extends AbstractCommandHandler<RecurringCommand> {
  public RecurringCommandHandler(ReplyProducer replyProducer) {
    super(replyProducer);
  }

  @Override
  public void run(RecurringCommand command) {
    sendCommand(command);
  }
}
