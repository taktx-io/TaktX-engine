package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ExternalTask;

public record ExternalTaskInfo(
    String externalTaskId,
    ExternalTask element,
    ExternalTaskInstance<?> instance,
    VariableScope variables,
    String startTime) {}
