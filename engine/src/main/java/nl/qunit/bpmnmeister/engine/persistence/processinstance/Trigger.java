package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.UUID;

public record Trigger(UUID processInstanceId, String elementId, String inputFlowId) {}
