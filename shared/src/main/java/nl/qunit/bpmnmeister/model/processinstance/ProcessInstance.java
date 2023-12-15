package nl.qunit.bpmnmeister.model.processinstance;

import java.util.Map;
import java.util.UUID;

public record ProcessInstance(UUID id, Map<String, BpmnElementState> elementStates) {}
