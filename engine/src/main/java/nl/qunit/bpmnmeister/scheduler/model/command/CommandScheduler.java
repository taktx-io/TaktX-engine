package nl.qunit.bpmnmeister.scheduler.model.command;

public interface CommandScheduler<T extends ScheduleCommand> {
  String schedule(T scheduleCommand);

  void cancel(String schedulerId);
}
