package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.Map;
import java.util.UUID;

public record ProcessInstance(
    UUID processInstanceId,
    String processDefinitionId,
    long version,
    Map<String, BpmnElementState> elementStates) {}
