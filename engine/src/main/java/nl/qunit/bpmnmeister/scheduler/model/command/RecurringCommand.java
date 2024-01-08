package nl.qunit.bpmnmeister.scheduler.model.command;

import java.util.List;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstanceTrigger;

public record RecurringCommand(String id, List<ProcessInstanceTrigger> triggers, String cron)
    implements ScheduleCommand {}
