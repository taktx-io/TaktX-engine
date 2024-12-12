package com.flomaestro.engine.pi.model;

public record TerminateCorrelationSubscriptionMessageEventInfo(
    String messageName, String correlationKey) {}
