package nl.qunit.bpmnmeister.pd.model;

import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

public record NewCorrelationSubscriptionMessageEventInfo(
    String messageName, String correlationKey, FLowNodeInstance instance) {}
