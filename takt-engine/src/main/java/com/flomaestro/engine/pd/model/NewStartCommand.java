package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.util.Set;
import java.util.UUID;

public record NewStartCommand(
    UUID processInstanceKey,
    FlowNode flowNode,
    FlowNodeInstance<?> instance,
    String calledElement,
    VariablesDTO variables,
    boolean propagateAllToParent,
    Set<IoVariableMapping> outputMappings) {}
