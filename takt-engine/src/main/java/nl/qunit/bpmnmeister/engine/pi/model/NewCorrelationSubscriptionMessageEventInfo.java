package nl.qunit.bpmnmeister.engine.pi.model;

public record NewCorrelationSubscriptionMessageEventInfo(
    String messageName, String correlationKey, ReceivingMessageInstance instance) {}
