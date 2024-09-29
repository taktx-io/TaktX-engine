package nl.qunit.bpmnmeister.pd.model;

import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

public record ScheduledContinuationInfo(
    FLowNodeInstance flowNodeInstance,
    TimerEventDefinition2 timerEventDefinition,
    Variables2 variables) {}
