package nl.qunit.bpmnmeister.pd.model;

import nl.qunit.bpmnmeister.pi.instances.ReceivingMessageInstance;

public record NewCorrelationSubscriptionMessageEventInfo(
    String messageName, String correlationKey, ReceivingMessageInstance instance) {}
