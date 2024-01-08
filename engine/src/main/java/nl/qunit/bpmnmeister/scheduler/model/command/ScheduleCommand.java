package nl.qunit.bpmnmeister.scheduler.model.command;

import java.util.List;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstanceTrigger;

public interface ScheduleCommand {
  String id();

  List<ProcessInstanceTrigger> triggers();
}
