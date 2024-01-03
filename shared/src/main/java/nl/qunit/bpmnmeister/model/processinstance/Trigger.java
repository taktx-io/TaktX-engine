package nl.qunit.bpmnmeister.model.processinstance;

import java.util.UUID;

public record Trigger(UUID processInstanceId, String elementId, String inputFlowId) {}
