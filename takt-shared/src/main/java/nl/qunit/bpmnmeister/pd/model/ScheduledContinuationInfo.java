package nl.qunit.bpmnmeister.pd.model;

import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.CatchEventInstance;

public record ScheduledContinuationInfo(
    CatchEventInstance<?> catchEventInstance,
    TimerEventDefinition timerEventDefinition,
    Variables variables) {}
