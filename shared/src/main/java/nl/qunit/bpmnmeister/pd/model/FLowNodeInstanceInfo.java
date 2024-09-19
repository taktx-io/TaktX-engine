package nl.qunit.bpmnmeister.pd.model;

import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

public record FLowNodeInstanceInfo(
    FLowNodeInstance<?> flowNodeInstance, String inputSequenceFlowId) {}
