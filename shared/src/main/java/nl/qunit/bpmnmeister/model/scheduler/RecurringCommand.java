package nl.qunit.bpmnmeister.model.scheduler;

import nl.qunit.bpmnmeister.model.processinstance.Trigger;

import java.util.List;

public record RecurringCommand(String id, List<Trigger> triggers, String cron) implements ScheduleCommand {
}
