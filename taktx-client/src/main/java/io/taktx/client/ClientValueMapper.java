/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.VariablesDTO;
import io.taktx.proto.VariableValue;
import io.taktx.variables.VariableObjectMapper;
import java.util.Map;

/** Lightweight reflection-based mapper used by the client module to avoid extra mapper dependencies. */
final class ClientValueMapper {

  private ClientValueMapper() {}

  static VariablesDTO toVariablesDto(Object source) {
    if (source instanceof VariablesDTO variablesDTO) {
      return variablesDTO;
    }
    return VariablesDTO.ofVariableMap(VariableObjectMapper.toVariableMap(source));
  }

  static Map<String, VariableValue> toVariableMap(Object source) {
    if (source == null) {
      return Map.of();
    }
    if (source instanceof VariablesDTO variablesDTO) {
      return variablesDTO.getVariables();
    }
    return VariableObjectMapper.toVariableMap(source);
  }

  static Map<String, Object> toPlainJavaMap(VariablesDTO variablesDTO) {
    return VariableObjectMapper.toPlainJavaMap(toVariableMap(variablesDTO));
  }

  static Object fromVariableValue(VariableValue value, Class<?> targetType) {
    return VariableObjectMapper.fromVariableValue(value, targetType);
  }

  static Object fromStringMap(Map<String, String> source, Class<?> targetType) {
    return VariableObjectMapper.fromJavaObject(source, targetType);
  }

  static Object fromVariablesDto(VariablesDTO variablesDTO, Class<?> targetType) {
    if (targetType == VariablesDTO.class) {
      return variablesDTO;
    }
    return VariableObjectMapper.fromVariableMap(
        variablesDTO == null ? Map.of() : variablesDTO.getVariables(), targetType);
  }

  static boolean isSimpleValue(Class<?> type) {
    return VariableObjectMapper.isSimpleValueType(type);
  }
}
