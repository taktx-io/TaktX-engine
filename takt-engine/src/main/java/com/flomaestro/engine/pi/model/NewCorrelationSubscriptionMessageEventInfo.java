package com.flomaestro.engine.pi.model;

public record NewCorrelationSubscriptionMessageEventInfo(
    String messageName, String correlationKey, ReceivingMessageInstance instance) {}
