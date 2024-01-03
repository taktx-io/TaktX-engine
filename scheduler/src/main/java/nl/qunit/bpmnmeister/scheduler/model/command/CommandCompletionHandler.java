package nl.qunit.bpmnmeister.scheduler.model.command;

import nl.qunit.bpmnmeister.model.scheduler.ScheduleCommand;

public interface CommandCompletionHandler {
  void commandCompleted(ScheduleCommand command);
}
