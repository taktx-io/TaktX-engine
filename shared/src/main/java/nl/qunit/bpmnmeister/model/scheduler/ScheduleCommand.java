package nl.qunit.bpmnmeister.model.scheduler;

import nl.qunit.bpmnmeister.model.processinstance.Trigger;

import java.util.List;

public interface ScheduleCommand {

    String id();
    List<Trigger> triggers();
}
