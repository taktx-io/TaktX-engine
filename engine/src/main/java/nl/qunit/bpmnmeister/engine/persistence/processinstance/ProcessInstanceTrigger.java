package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.UUID;

public record ProcessInstanceTrigger(
    UUID processInstanceId,
    String processDefinitionId,
    long version,
    String elementId,
    String inputFlowId) {}
