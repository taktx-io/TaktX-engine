package nl.qunit.bpmnmeister.engine;

import java.util.UUID;

public record ExternalTaskCommand(String taskId, UUID processInstanceId) {}
