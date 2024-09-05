package nl.qunit.bpmnmeister.pi;

import nl.qunit.bpmnmeister.pd.model.ExternalTask2;
import nl.qunit.bpmnmeister.pi.instances.ExternalTaskInstance;

public record ExternalTaskInfo(
    String externalTaskId,
    ExternalTask2 element,
    ExternalTaskInstance instance,
    Variables2 variables,
    String startTime) {}
