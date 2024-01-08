package nl.qunit.bpmnmeister.scheduler.model.one_time;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import jakarta.enterprise.context.Dependent;
import java.time.ZonedDateTime;
import nl.qunit.bpmnmeister.scheduler.model.command.AbstractCommandScheduler;
import nl.qunit.bpmnmeister.scheduler.model.command.CommandScheduler;
import nl.qunit.bpmnmeister.scheduler.model.command.OneTimeCommand;

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

  private String convertToCronExpression(String when) {
    // Convert the ZonedDateTime string to a cron expression
    ZonedDateTime zonedDateTime = ZonedDateTime.parse(when);
    return String.format(
        "%d %d %d %d * ?",
        zonedDateTime.getSecond(),
        zonedDateTime.getMinute(),
        zonedDateTime.getHour(),
        zonedDateTime.getDayOfMonth());
  }
}
