package nl.qunit.bpmnmeister.pi;

import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

public record NewStartCommand(
    UUID processInstanceKey,
    UUID parentProcessInstanceKey,
    FlowNode2 flowNode,
    FLowNodeInstance instance,
    String calledElement,
    Variables2 variables) {}
