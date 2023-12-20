package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.Set;

public record TriggerResult(
    BpmnElementState newElementState, Set<String> newActiveFlows, Set<String> externalTasks) {}
