package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.BpmnElementState;

public record TriggerResult(BpmnElementState newElementState, Set<String> newActiveFlows) {}
