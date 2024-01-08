package nl.qunit.bpmnmeister.scheduler.model.command;

public interface CommandCompletionHandler {
  void commandCompleted(ScheduleCommand command);
}
