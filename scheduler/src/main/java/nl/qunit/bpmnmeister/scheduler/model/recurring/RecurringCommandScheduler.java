package nl.qunit.bpmnmeister.scheduler.model.recurring;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.model.scheduler.RecurringCommand;
import nl.qunit.bpmnmeister.scheduler.model.command.AbstractCommandScheduler;
import nl.qunit.bpmnmeister.scheduler.model.command.CommandScheduler;

@Dependent
public class RecurringCommandScheduler extends AbstractCommandScheduler<RecurringCommand>
    implements CommandScheduler<RecurringCommand> {

  @Inject
  public RecurringCommandScheduler(Scheduler scheduler, RecurringCommandHandler commandHandler) {
    super(scheduler, commandHandler);
  }

  @Override
  public String schedule(RecurringCommand command) {
    if (isValidCronExpression(command.cron())) {
      Trigger trigger =
          scheduler
              .newJob(command.id().toString())
              .setCron(command.cron())
              .setTask(scheduledExecution -> commandHandler.run(command))
              .schedule();

      return trigger.getId();
    } else {
      throw new IllegalArgumentException("Invalid timeCycle expression");
    }
  }

  public boolean isValidCronExpression(String potentialCtronExpression) {
    try {
      CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
      CronParser parser = new CronParser(cronDefinition);
      Cron cron = parser.parse(potentialCtronExpression);
      cron.validate();
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
