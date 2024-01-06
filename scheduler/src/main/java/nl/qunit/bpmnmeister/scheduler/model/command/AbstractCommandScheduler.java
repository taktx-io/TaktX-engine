package nl.qunit.bpmnmeister.scheduler.model.command;

import io.quarkus.scheduler.Scheduler;
import nl.qunit.bpmnmeister.model.scheduler.ScheduleCommand;

public abstract class AbstractCommandScheduler<C extends ScheduleCommand> {

  protected final Scheduler scheduler;
  protected final AbstractCommandHandler<C> commandHandler;

  protected AbstractCommandScheduler(
      Scheduler scheduler, AbstractCommandHandler<C> commandHandler) {
    this.scheduler = scheduler;
    this.commandHandler = commandHandler;
  }

  public void cancel(String schedulerId) {
    scheduler.unscheduleJob(schedulerId);
  }
}
