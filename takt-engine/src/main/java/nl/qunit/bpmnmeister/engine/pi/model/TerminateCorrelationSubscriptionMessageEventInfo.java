package nl.qunit.bpmnmeister.engine.pi.model;

public record TerminateCorrelationSubscriptionMessageEventInfo(
    String messageName, String correlationKey) {}
