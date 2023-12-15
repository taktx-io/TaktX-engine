package nl.qunit.bpmnmeister.model.processinstance;

import java.util.Set;

public record TriggerResult(
    BpmnElementState newState, Set<String> newActiveFlows, Set<String> externalTasks) {}
