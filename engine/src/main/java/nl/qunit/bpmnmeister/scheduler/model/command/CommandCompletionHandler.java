package nl.qunit.bpmnmeister.scheduler.model.command;

import nl.qunit.bpmnmeister.scheduler.ScheduleCommand;

public interface CommandCompletionHandler {
  void commandCompleted(ScheduleCommand command);
}
