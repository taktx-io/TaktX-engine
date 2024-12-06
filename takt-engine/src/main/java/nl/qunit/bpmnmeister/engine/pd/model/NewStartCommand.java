package nl.qunit.bpmnmeister.engine.pd.model;

import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.Variables;

public record NewStartCommand(
    UUID processInstanceKey,
    UUID parentProcessInstanceKey,
    FlowNode flowNode,
    FLowNodeInstance<?> instance,
    String calledElement,
    Variables variables) {}
