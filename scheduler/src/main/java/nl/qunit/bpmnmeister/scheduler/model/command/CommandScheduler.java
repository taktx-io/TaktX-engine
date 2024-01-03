package nl.qunit.bpmnmeister.scheduler.model.command;

import nl.qunit.bpmnmeister.model.scheduler.ScheduleCommand;

public interface CommandScheduler<T extends ScheduleCommand> {
  String schedule(T scheduleCommand);

  void cancel(String schedulerId);
}
