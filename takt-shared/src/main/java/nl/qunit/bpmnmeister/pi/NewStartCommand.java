package nl.qunit.bpmnmeister.pi;

import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

public record NewStartCommand(
    UUID processInstanceKey,
    UUID parentProcessInstanceKey,
    FlowNode flowNode,
    FLowNodeInstance<?> instance,
    String calledElement,
    Variables variables) {}
