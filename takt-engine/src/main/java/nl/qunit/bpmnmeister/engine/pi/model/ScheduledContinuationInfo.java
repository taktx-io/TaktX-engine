package nl.qunit.bpmnmeister.engine.pi.model;

import nl.qunit.bpmnmeister.engine.pd.model.TimerEventDefinition;
import nl.qunit.bpmnmeister.pi.Variables;

public record ScheduledContinuationInfo(
    CatchEventInstance<?> catchEventInstance,
    TimerEventDefinition timerEventDefinition,
    Variables variables) {}
