/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

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
