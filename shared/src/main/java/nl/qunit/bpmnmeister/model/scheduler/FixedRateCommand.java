package nl.qunit.bpmnmeister.model.scheduler;

import nl.qunit.bpmnmeister.model.processinstance.Trigger;

import java.util.List;
import java.util.UUID;

public record FixedRateCommand(UUID id, List<Trigger> triggers, String period, int repetitions, int repeatedCnt) implements ScheduleCommand {

    public static final String PERIOD = "kafka-scheduler-perdiod";

}
