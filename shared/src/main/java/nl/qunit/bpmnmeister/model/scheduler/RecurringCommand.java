package nl.qunit.bpmnmeister.model.scheduler;

import nl.qunit.bpmnmeister.model.processinstance.Trigger;

import java.util.List;
import java.util.UUID;

public record RecurringCommand(UUID id, List<Trigger> triggers, String timeCycle) implements ScheduleCommand {
}
