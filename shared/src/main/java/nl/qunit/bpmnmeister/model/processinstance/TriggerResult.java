package nl.qunit.bpmnmeister.model.processinstance;

import java.util.Set;

public record TriggerResult(
    BpmnElementState newElementState, Set<String> newActiveFlows, Set<String> externalTasks) {}
