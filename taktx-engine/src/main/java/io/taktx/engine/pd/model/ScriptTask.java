/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import io.taktx.dto.ScriptType;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.ScriptTaskInstance;
import io.taktx.engine.pi.model.WithScope;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ScriptTask extends ExternalTask {
  private ScriptType scriptType;

  private List<String> scriptExpressions;

  private String resultVariableName;

  @Override
  public ActivityInstance<?> newActivityInstance(WithScope parentInstance, long elementInstanceId) {
    return new ScriptTaskInstance(parentInstance, this, elementInstanceId);
  }
}
