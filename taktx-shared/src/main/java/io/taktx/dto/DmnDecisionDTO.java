/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Represents a single DMN decision (either a decision table or a literal expression). Exactly one
 * of {@code decisionTable} or {@code literalExpression} will be non-null.
 */
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@RegisterForReflection
public class DmnDecisionDTO {
  private String id;
  private String name;

  /** Non-null when the decision uses a DecisionTable. */
  private DmnDecisionTableDTO decisionTable;

  /** Non-null when the decision uses a LiteralExpression. */
  private DmnLiteralExpressionDTO literalExpression;

  /**
   * IDs of decisions this decision requires (DRG information requirements). Null or empty when this
   * decision has no upstream dependencies.
   */
  private List<String> requiredDecisionIds;
}
