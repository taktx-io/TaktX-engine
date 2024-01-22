package nl.qunit.bpmnmeister.scheduler.model.command;

import nl.qunit.bpmnmeister.scheduler.ScheduleCommand;

public interface CommandScheduler<T extends ScheduleCommand> {
  String schedule(T scheduleCommand);

  void cancel(String schedulerId);
}
