package nl.qunit.bpmnmeister.scheduler.model.fixedrate;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.scheduler.model.command.AbstractCommandScheduler;
import nl.qunit.bpmnmeister.scheduler.model.command.CommandScheduler;
import nl.qunit.bpmnmeister.scheduler.FixedRateCommand;

@Dependent
public class FixedRateCommandScheduler extends AbstractCommandScheduler<FixedRateCommand>
    implements CommandScheduler<FixedRateCommand> {
  @Inject
  public FixedRateCommandScheduler(Scheduler scheduler, FixedRateCommandHandler commandHandler) {
    super(scheduler, commandHandler);
  }

  @Override
  public String schedule(FixedRateCommand command) {
    Trigger trigger =
        scheduler
            .newJob(command.scheduleKey().toString())
            .setInterval(command.period())
            .setTask(scheduledExecution -> commandHandler.run(command))
            .schedule();

    return trigger.getId();
  }
}
