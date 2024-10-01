package nl.qunit.bpmnmeister.pd.model;

import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.CatchEventInstance;

public record ScheduledContinuationInfo(
    CatchEventInstance<?> catchEventInstance,
    TimerEventDefinition2 timerEventDefinition,
    Variables2 variables) {}
