package nl.qunit.bpmnmeister.engine.pi.model;

import nl.qunit.bpmnmeister.engine.pd.model.ExternalTask;
import nl.qunit.bpmnmeister.pi.Variables;

public record ExternalTaskInfo(
    String externalTaskId,
    ExternalTask element,
    ExternalTaskInstance<?> instance,
    Variables variables,
    String startTime) {}
