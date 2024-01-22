package nl.qunit.bpmnmeister.scheduler;

import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

import java.util.List;

public record RecurringCommand(ScheduleKey scheduleKey, List<ProcessInstanceTrigger> triggers, String cron)
    implements ScheduleCommand {}
