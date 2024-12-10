package nl.qunit.bpmnmeister.engine.pi.model;

import nl.qunit.bpmnmeister.engine.pd.model.ExternalTask;

public record ExternalTaskInfo(
    String externalTaskId,
    ExternalTask element,
    ExternalTaskInstance<?> instance,
    Variables variables,
    String startTime) {}
