package nl.qunit.bpmnmeister.scheduler;

import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

import java.util.List;

public interface ScheduleCommand {
  ScheduleKey scheduleKey();

  List<ProcessInstanceTrigger> triggers();
}
