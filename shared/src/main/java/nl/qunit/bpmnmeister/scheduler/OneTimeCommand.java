package nl.qunit.bpmnmeister.scheduler;

import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

import java.util.List;

public record OneTimeCommand(ScheduleKey scheduleKey, List<ProcessInstanceTrigger> triggers, String when)
    implements ScheduleCommand {}
