package nl.qunit.bpmnmeister.scheduler.model.command;

import io.quarkus.scheduler.Scheduler;

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
