/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pd.model.WithIoMapping;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import lombok.Setter;

@ApplicationScoped
@Setter
public class IoMappingProcessor {

  private FeelExpressionHandler feelExpressionHandler;

  @Inject
  public IoMappingProcessor(FeelExpressionHandler feelExpressionHandler) {
    this.feelExpressionHandler = feelExpressionHandler;
  }

  public void processOutputMappings(WithIoMapping element, VariableScope variables) {
    Set<IoVariableMapping> outputMappings = element.getIoMapping().getOutputMappings();

    addVariables(variables, outputMappings);
  }

  public void addVariables(VariableScope variables, Set<IoVariableMapping> mappings) {
    if (mappings.isEmpty()) {
      variables.mergeAllToParent();
    } else {
      for (IoVariableMapping mapping : mappings) {
        String varName = mapping.getTarget();
        JsonNode jsonNode =
            feelExpressionHandler.processFeelExpression(mapping.getSource(), variables);
        variables.getParentScope().put(varName, jsonNode);
      }
    }
  }

  public void addInputVariables(WithIoMapping element, VariableScope variables) {
    Set<IoVariableMapping> inputMappings = element.getIoMapping().getInputMappings();
    if (inputMappings.isEmpty()) {
      return;
    }

    addVariables(variables, inputMappings);
  }
}
