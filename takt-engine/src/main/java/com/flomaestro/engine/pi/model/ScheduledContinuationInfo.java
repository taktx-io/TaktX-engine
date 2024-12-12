package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.TimerEventDefinition;

public record ScheduledContinuationInfo(
    CatchEventInstance<?> catchEventInstance,
    TimerEventDefinition timerEventDefinition,
    Variables variables) {}
