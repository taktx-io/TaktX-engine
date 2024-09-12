package nl.qunit.bpmnmeister.pd.model;

public record TerminateCorrelationSubscriptionMessageEventInfo(
    String messageName, String correlationKey) {}
