/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class InputOutputMapping {
  private final Set<IoVariableMapping> inputMappings;
  private final Set<IoVariableMapping> outputMappings;

  public InputOutputMapping(
      @Nonnull Set<IoVariableMapping> inputMappings,
      @Nonnull Set<IoVariableMapping> outputMappings) {
    this.inputMappings = inputMappings;
    this.outputMappings = outputMappings;
  }
}
