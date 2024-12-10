package nl.qunit.bpmnmeister.engine.pi.model;

public record FlowNodeInstanceInfo(
    FLowNodeInstance<?> flowNodeInstance, String inputSequenceFlowId) {

}
