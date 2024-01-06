package nl.qunit.bpmnmeister.scheduler.model.one_time;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import jakarta.enterprise.context.Dependent;
import java.time.ZonedDateTime;
import nl.qunit.bpmnmeister.model.scheduler.OneTimeCommand;
import nl.qunit.bpmnmeister.scheduler.model.command.AbstractCommandScheduler;
import nl.qunit.bpmnmeister.scheduler.model.command.CommandScheduler;

@Dependent
public class OneTimeCommandScheduler extends AbstractCommandScheduler<OneTimeCommand>
    implements CommandScheduler<OneTimeCommand> {

  public OneTimeCommandScheduler(Scheduler scheduler, OneTimeCommandHandler handler) {
    super(scheduler, handler);
  }

  public String schedule(OneTimeCommand command) {

    Trigger trigger =
        scheduler
            .newJob(command.id().toString())
            .setCron(convertToCronExpression(command.when()))
            .setTask(scheduledExecution -> commandHandler.run(command))
            .schedule();

    return trigger.getId();
  }

  private String convertToCronExpression(ZonedDateTime when) {
    // Convert the ZonedDateTime to a cron expression
    // This is just a placeholder and should be replaced with your actual conversion logic
    return String.format(
        "%d %d %d %d * ?",
        when.getSecond(), when.getMinute(), when.getHour(), when.getDayOfMonth());
  }
}
