/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.proto.VariableValue;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record NewStartCommand(
    UUID processInstanceId,
    FlowNode flowNode,
    FlowNodeInstance<?> instance,
    String calledElement,
    Map<String, VariableValue> variables,
    boolean propagateAllToParent,
    Set<IoVariableMapping> outputMappings) {}
