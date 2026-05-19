/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.variables;

import io.taktx.proto.VariableValue;

/**
 * Pluggable adapter for Java leaf types that should map to and from {@link VariableValue}.
 *
 * <p>Adapters are intended for scalar-like value types such as {@code UUID} or {@code Instant}.
 * They are consulted before reflective bean mapping. Implementations should therefore be
 * deterministic, fast, and side-effect free.
 */
public interface VariableTypeAdapter {

  /** Returns whether this adapter supports the provided Java type. */
  boolean supports(Class<?> type);

  /** Encodes the supplied Java value as a {@link VariableValue}. */
  VariableValue toVariableValue(Object value);

  /** Decodes a plain Java value into the requested target type. */
  Object fromJavaObject(Object value, Class<?> targetType);
}
