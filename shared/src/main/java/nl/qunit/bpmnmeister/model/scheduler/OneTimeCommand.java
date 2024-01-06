package nl.qunit.bpmnmeister.model.scheduler;


import nl.qunit.bpmnmeister.model.processinstance.Trigger;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record OneTimeCommand(UUID id, List<Trigger> triggers, ZonedDateTime when) implements ScheduleCommand {

}
