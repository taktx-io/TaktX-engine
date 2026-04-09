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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@RegisterForReflection
public class DmnDefinitionKey implements Comparable<DmnDefinitionKey> {
  private String dmnDefinitionId;
  private Integer version;

  public DmnDefinitionKey(String dmnDefinitionId) {
    this(dmnDefinitionId, -1);
  }

  public DmnDefinitionKey(String dmnDefinitionId, Integer version) {
    this.dmnDefinitionId = dmnDefinitionId;
    this.version = version;
  }

  @Override
  public int compareTo(DmnDefinitionKey other) {
    int cmp = this.dmnDefinitionId.compareTo(other.dmnDefinitionId);
    if (cmp != 0) return cmp;
    return Integer.compare(this.version, other.version);
  }
}
