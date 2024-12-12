package com.flomaestro.engine.pi.model;

public record FlowNodeInstanceInfo(
    FlowNodeInstance<?> flowNodeInstance, String inputSequenceFlowId) {}
