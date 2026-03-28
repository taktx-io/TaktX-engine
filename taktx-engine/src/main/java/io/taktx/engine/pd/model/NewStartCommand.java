/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.model.FlowNodeInstance;
import java.util.Set;
import java.util.UUID;

public record NewStartCommand(
    UUID processInstanceId,
    FlowNode flowNode,
    FlowNodeInstance<?> instance,
    String calledElement,
    VariablesDTO variables,
    boolean propagateAllToParent,
    Set<IoVariableMapping> outputMappings) {}
