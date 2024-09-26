package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.pd.model.SequenceFlow2;
import nl.qunit.bpmnmeister.pi.instances.InclusiveGatewayInstance;

public record GatewayOutgoingFlowPair(
    InclusiveGatewayInstance inclusiveGatewayInstance, SequenceFlow2 outputFlow) {}
