package nl.qunit.bpmnmeister.model.processinstance;

import java.util.UUID;

public record Trigger(UUID processInstanceId, String processDefinition, long version, String elementId, String inputFlowId, UUID timerId) {}
