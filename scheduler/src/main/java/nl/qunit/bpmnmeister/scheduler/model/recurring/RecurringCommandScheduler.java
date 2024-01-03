package nl.qunit.bpmnmeister.scheduler.model.recurring;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.model.scheduler.RecurringCommand;
import nl.qunit.bpmnmeister.scheduler.model.command.AbstractCommandScheduler;
import nl.qunit.bpmnmeister.scheduler.model.command.CommandHandler;
import nl.qunit.bpmnmeister.scheduler.model.command.CommandScheduler;

@Dependent
public class RecurringCommandScheduler extends AbstractCommandScheduler
    implements CommandScheduler<RecurringCommand> {

  @Inject
  public RecurringCommandScheduler(Scheduler jobRequestScheduler, CommandHandler commandHandler) {
    super(jobRequestScheduler, commandHandler);
  }

  public String schedule(RecurringCommand command) {
    Trigger trigger =
        scheduler
            .newJob(command.id())
            .setCron(command.cron())
            .setTask(scheduledExecution -> commandHandler.sendCommand(command))
            .schedule();

    return trigger.getId();
  }
}
