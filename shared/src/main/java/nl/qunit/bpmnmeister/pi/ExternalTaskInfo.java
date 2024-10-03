package nl.qunit.bpmnmeister.pi;

import nl.qunit.bpmnmeister.pd.model.ExternalTask;
import nl.qunit.bpmnmeister.pi.instances.ExternalTaskInstance;

public record ExternalTaskInfo(
    String externalTaskId,
    ExternalTask element,
    ExternalTaskInstance<?> instance,
    Variables variables,
    String startTime) {}
