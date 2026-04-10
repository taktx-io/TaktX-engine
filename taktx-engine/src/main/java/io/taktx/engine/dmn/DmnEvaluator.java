/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.dmn;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.DmnDecisionDTO;
import io.taktx.engine.pi.model.VariableScope;

public interface DmnEvaluator {

  /**
   * Evaluates a DMN decision against the given variables.
   *
   * @param decision the DMN decision to evaluate
   * @param variables the process-instance variable scope
   * @return the decision result as a JsonNode (object for single-row policies, array for multi-row
   *     policies such as COLLECT/RULE_ORDER/OUTPUT_ORDER)
   */
  JsonNode evaluate(DmnDecisionDTO decision, VariableScope variables);
}
