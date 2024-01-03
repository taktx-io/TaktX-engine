package nl.qunit.bpmnmeister.scheduler.model.command;

import io.quarkus.scheduler.Scheduler;

public abstract class AbstractCommandScheduler {

  protected final Scheduler scheduler;
  protected final CommandHandler commandHandler;

  protected AbstractCommandScheduler(Scheduler scheduler, CommandHandler commandHandler) {
    this.scheduler = scheduler;
    this.commandHandler = commandHandler;
  }

  public void cancel(String schedulerId) {
    scheduler.unscheduleJob(schedulerId);
  }
}
