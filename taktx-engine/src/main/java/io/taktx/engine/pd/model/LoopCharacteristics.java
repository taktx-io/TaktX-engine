/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class LoopCharacteristics {
  public static final LoopCharacteristics NONE = new LoopCharacteristics(false, "", "", "", "");
  private final boolean sequential;
  private final String inputCollection;
  private final String inputElement;
  private final String outputCollection;
  private final String outputElement;

  public LoopCharacteristics(
      boolean sequential,
      String inputCollection,
      String inputElement,
      String outputCollection,
      String outputElement) {
    this.sequential = sequential;
    this.inputCollection = inputCollection;
    this.inputElement = inputElement;
    this.outputCollection = outputCollection;
    this.outputElement = outputElement;
  }
}
