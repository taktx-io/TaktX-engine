/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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

  public void processInputMappings(WithIoMapping element, VariableScope variables) {
    Set<IoVariableMapping> inputMappings = element.getIoMapping().getInputMappings();
    if (inputMappings.isEmpty()) {
      return;
    }

    addVariables(variables.getParentScope(), variables, inputMappings);
  }

  /**
   * Applies output mappings from the element's local scope to its parent scope, then closes the
   * child scope.
   *
   * <p><b>No output mappings defined (Zeebe-compatible behaviour):</b> all dirty (locally written)
   * variables are propagated to the parent scope so that downstream nodes such as gateways can read
   * them.
   *
   * <p><b>Explicit output mappings defined:</b> only the mapped variables are evaluated and copied
   * to the parent scope. Unmapped local variables are not accessible to downstream nodes (because
   * {@code VariableScope.get()} only walks <em>up</em> the scope chain), so they are dropped.
   *
   * <p>In both cases the child scope is <em>closed</em> after propagation: both the in-memory
   * {@code variables} map and the {@code dirtyVariables} set are cleared. This prevents:
   *
   * <ul>
   *   <li>duplicate Kafka writes – {@code persistTree()} would otherwise write the same value at
   *       the child scope path in addition to the parent scope path; and
   *   <li>unnecessary heap retention – the child scope object remains in the parent's {@code
   *       childScopes} map but carries no live variable data.
   * </ul>
   *
   * <p>Note: the calling {@code FlowNodeInstanceProcessor} creates the {@code
   * FlowNodeInstanceUpdateDTO} <em>after</em> this method returns, so the task-level update will
   * carry empty variables. The propagated values are visible in the enclosing scope's update
   * instead, which is consistent with Zeebe's audit model.
   *
   * @param element the flow-node that carries the I/O mapping declaration
   * @param variables the element's local variable scope (child scope)
   */
  public void processOutputMappings(WithIoMapping element, VariableScope variables) {
    Set<IoVariableMapping> outputMappings = element.getIoMapping().getOutputMappings();

    if (outputMappings.isEmpty()) {
      // No explicit output mappings → propagate all dirty local variables to the parent scope.
      VariableScope parent = variables.getParentScope();
      if (parent != null) {
        variables
            .getDirtyVariables()
            .forEach(key -> parent.put(key, variables.getVariables().get(key)));
      }
    } else {
      // Explicit mappings → evaluate and copy only the declared variables to the parent scope.
      addVariables(variables, variables.getParentScope(), outputMappings);
    }

    // Close the child scope: release in-memory state and prevent duplicate Kafka persistence.
    variables.getDirtyVariables().clear();
    variables.getVariables().clear();
  }

  public void addVariables(
      VariableScope source, VariableScope target, Set<IoVariableMapping> mappings) {
    for (IoVariableMapping mapping : mappings) {
      String varName = mapping.getTarget();
      JsonNode jsonNode = feelExpressionHandler.processFeelExpression(mapping.getSource(), source);
      setNestedVariable(target, varName, jsonNode);
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
}
