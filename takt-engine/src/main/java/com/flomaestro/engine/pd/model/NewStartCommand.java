package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.Variables;
import java.util.UUID;

public record NewStartCommand(
    UUID processInstanceKey,
    UUID parentProcessInstanceKey,
    FlowNode flowNode,
    FlowNodeInstance<?> instance,
    String calledElement,
    Variables variables) {}
