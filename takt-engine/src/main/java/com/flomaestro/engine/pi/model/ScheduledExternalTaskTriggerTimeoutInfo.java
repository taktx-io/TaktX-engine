package com.flomaestro.engine.pi.model;

public record ScheduledExternalTaskTriggerTimeoutInfo(
    ExternalTaskInstance externalTaskInstance, long timeoutMs) {}
