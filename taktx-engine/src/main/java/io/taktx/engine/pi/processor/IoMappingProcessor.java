/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pd.model.WithIoMapping;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.proto.VariableValue;
import io.taktx.variables.Variables;
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
      VariableScope parent = variables.getParentScope();
      if (parent != null) {
        variables
            .getDirtyVariables()
            .forEach(key -> parent.put(key, variables.getVariables().get(key)));
      }
    } else {
      addVariables(variables, variables.getParentScope(), outputMappings);
    }

    variables.getDirtyVariables().clear();
    variables.getVariables().clear();
  }

  public void addVariables(
      VariableScope source, VariableScope target, Set<IoVariableMapping> mappings) {
    for (IoVariableMapping mapping : mappings) {
      String varName = mapping.getTarget();
      VariableValue value =
          feelExpressionHandler.processFeelExpression(mapping.getSource(), source);
      setNestedVariable(target, varName, value);
    }
  }

  private void setNestedVariable(VariableScope variables, String varName, VariableValue value) {
    if (!varName.contains(".")) {
      variables.put(varName, value);
      return;
    }

    String[] pathParts = varName.split("\\.");
    String rootVarName = pathParts[0];

    java.util.Map<String, VariableValue> rootObject =
        asMutableMap(variables.getVariables().get(rootVarName));
    setNestedValue(rootObject, pathParts, 1, value == null ? Variables.nullValue() : value);

    variables.put(
        rootVarName,
        VariableValue.newBuilder().setMapValue(Variables.toVarMap(rootObject)).build());
  }

  private void setNestedValue(
      java.util.Map<String, VariableValue> current,
      String[] pathParts,
      int index,
      VariableValue value) {
    if (index == pathParts.length - 1) {
      current.put(pathParts[index], value);
      return;
    }
    String key = pathParts[index];
    java.util.Map<String, VariableValue> child = asMutableMap(current.get(key));
    setNestedValue(child, pathParts, index + 1, value);
    current.put(key, VariableValue.newBuilder().setMapValue(Variables.toVarMap(child)).build());
  }

  private java.util.Map<String, VariableValue> asMutableMap(VariableValue value) {
    java.util.Map<String, VariableValue> result = new java.util.LinkedHashMap<>();
    if (value != null && value.getKindCase() == VariableValue.KindCase.MAP_VALUE) {
      result.putAll(value.getMapValue().getEntriesMap());
    }
    return result;
  }
}
