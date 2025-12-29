/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private ObjectMapper objectMapper;

  @Inject
  public IoMappingProcessor(
      FeelExpressionHandler feelExpressionHandler, ObjectMapper objectMapper) {
    this.feelExpressionHandler = feelExpressionHandler;
    this.objectMapper = objectMapper;
  }

  public void processOutputMappings(WithIoMapping element, VariableScope variables) {
    Set<IoVariableMapping> outputMappings = element.getIoMapping().getOutputMappings();

    addVariables(variables, outputMappings);
  }

  public void addVariables(VariableScope variables, Set<IoVariableMapping> mappings) {
    if (!mappings.isEmpty()) {
      for (IoVariableMapping mapping : mappings) {
        String varName = mapping.getTarget();
        JsonNode jsonNode =
            feelExpressionHandler.processFeelExpression(mapping.getSource(), variables);
        setNestedVariable(variables, varName, jsonNode);
      }
    }
  }

  private void setNestedVariable(VariableScope variables, String varName, JsonNode value) {
    if (!varName.contains(".")) {
      // Simple variable name without nesting
      variables.put(varName, value);
      return;
    }

    // Split the variable name by dots to get the path
    String[] pathParts = varName.split("\\.");
    String rootVarName = pathParts[0];

    // Get or create the root object
    JsonNode rootNode = variables.getVariables().get(rootVarName);
    com.fasterxml.jackson.databind.node.ObjectNode rootObject;

    if (rootNode == null || !rootNode.isObject()) {
      // Create a new root object if it doesn't exist or is not an object
      rootObject = objectMapper.createObjectNode();
    } else {
      // Use existing object but make it mutable
      rootObject = (com.fasterxml.jackson.databind.node.ObjectNode) rootNode;
    }

    // Navigate/create the nested path
    com.fasterxml.jackson.databind.node.ObjectNode currentObject = rootObject;
    for (int i = 1; i < pathParts.length - 1; i++) {
      String key = pathParts[i];
      JsonNode childNode = currentObject.get(key);

      if (childNode == null || !childNode.isObject()) {
        // Create a new nested object if it doesn't exist or is not an object
        com.fasterxml.jackson.databind.node.ObjectNode newObject = objectMapper.createObjectNode();
        currentObject.set(key, newObject);
        currentObject = newObject;
      } else {
        currentObject = (com.fasterxml.jackson.databind.node.ObjectNode) childNode;
      }
    }

    // Set the final value at the deepest level
    String finalKey = pathParts[pathParts.length - 1];
    currentObject.set(finalKey, value);

    // Store the modified root object back to variables
    variables.put(rootVarName, rootObject);
  }

  public void addInputVariables(WithIoMapping element, VariableScope variables) {
    Set<IoVariableMapping> inputMappings = element.getIoMapping().getInputMappings();
    if (inputMappings.isEmpty()) {
      return;
    }

    addVariables(variables, inputMappings);
  }
}
