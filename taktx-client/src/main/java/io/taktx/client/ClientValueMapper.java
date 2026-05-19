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
import io.taktx.variables.VariableValueDtoMapper;
import io.taktx.variables.Variables;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Lightweight reflection-based mapper used by the client module to avoid Jackson dependencies. */
final class ClientValueMapper {

  private ClientValueMapper() {}

  static VariablesDTO toVariablesDto(Object source) {
    if (source instanceof VariablesDTO variablesDTO) {
      return variablesDTO;
    }
    return VariableValueDtoMapper.toVariablesDto(toVariableMap(source));
  }

  static Map<String, VariableValue> toVariableMap(Object source) {
    if (source == null) {
      return Map.of();
    }
    if (source instanceof VariablesDTO variablesDTO) {
      return VariableValueDtoMapper.toVariableMap(variablesDTO);
    }
    if (source instanceof Map<?, ?> map) {
      LinkedHashMap<String, VariableValue> result = new LinkedHashMap<>();
      map.forEach(
          (key, value) ->
              result.put(
                  String.valueOf(key),
                  value instanceof VariableValue variableValue
                      ? variableValue
                      : Variables.of(value)));
      return result;
    }
    return toVariableMap(extractProperties(source));
  }

  static Map<String, Object> toPlainJavaMap(VariablesDTO variablesDTO) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    VariableValueDtoMapper.toVariableMap(variablesDTO)
        .forEach((key, value) -> result.put(key, Variables.toJavaObject(value)));
    return result;
  }

  static Object fromVariableValue(VariableValue value, Class<?> targetType) {
    if (targetType == VariableValue.class) {
      return value;
    }
    return convertValue(Variables.toJavaObject(value), targetType);
  }

  static Object fromStringMap(Map<String, String> source, Class<?> targetType) {
    return convertValue(source, targetType);
  }

  static Object convertValue(Object value, Class<?> targetType) {
    if (targetType == null || targetType == Object.class) {
      return value;
    }
    Class<?> boxedTargetType = box(targetType);
    if (value == null) {
      return null;
    }
    if (boxedTargetType.isInstance(value)) {
      return value;
    }
    if (boxedTargetType == String.class) {
      return String.valueOf(value);
    }
    if (boxedTargetType == Long.class) {
      return toLong(value);
    }
    if (boxedTargetType == Integer.class) {
      return Math.toIntExact(toLong(value));
    }
    if (boxedTargetType == Short.class) {
      return (short) toLong(value);
    }
    if (boxedTargetType == Byte.class) {
      return (byte) toLong(value);
    }
    if (boxedTargetType == Double.class) {
      return toDouble(value);
    }
    if (boxedTargetType == Float.class) {
      return (float) toDouble(value);
    }
    if (boxedTargetType == Boolean.class) {
      return toBoolean(value);
    }
    if (boxedTargetType == UUID.class && value instanceof String text) {
      return UUID.fromString(text);
    }
    if (boxedTargetType.isEnum()) {
      @SuppressWarnings({"rawtypes", "unchecked"})
      Object enumValue =
          Enum.valueOf((Class<? extends Enum>) boxedTargetType, String.valueOf(value));
      return enumValue;
    }
    if (boxedTargetType == VariablesDTO.class) {
      return toVariablesDto(value);
    }
    if (Map.class.isAssignableFrom(boxedTargetType) && value instanceof Map<?, ?>) {
      return value;
    }
    if (value instanceof Map<?, ?> map) {
      return instantiateFromMap(map, boxedTargetType);
    }
    return instantiateFromMap(extractProperties(value), boxedTargetType);
  }

  private static Object instantiateFromMap(Map<?, ?> source, Class<?> targetType) {
    try {
      if (targetType.isRecord()) {
        return instantiateRecord(source, targetType);
      }
      Constructor<?> constructor = targetType.getDeclaredConstructor();
      Object instance = constructor.newInstance();
      for (PropertyDescriptor descriptor :
          Introspector.getBeanInfo(targetType, Object.class).getPropertyDescriptors()) {
        if (descriptor.getWriteMethod() == null || !source.containsKey(descriptor.getName())) {
          continue;
        }
        Object rawValue = source.get(descriptor.getName());
        Object convertedValue = convertValue(rawValue, descriptor.getPropertyType());
        descriptor.getWriteMethod().invoke(instance, convertedValue);
      }
      return instance;
    } catch (ReflectiveOperationException | java.beans.IntrospectionException e) {
      throw new IllegalArgumentException(
          "Cannot map " + source.getClass().getName() + " to " + targetType.getName(), e);
    }
  }

  private static Object instantiateRecord(Map<?, ?> source, Class<?> targetType)
      throws ReflectiveOperationException {
    RecordComponent[] components = targetType.getRecordComponents();
    Class<?>[] parameterTypes = new Class<?>[components.length];
    Object[] arguments = new Object[components.length];
    for (int i = 0; i < components.length; i++) {
      RecordComponent component = components[i];
      parameterTypes[i] = component.getType();
      arguments[i] = convertValue(source.get(component.getName()), component.getType());
    }
    Constructor<?> constructor = targetType.getDeclaredConstructor(parameterTypes);
    return constructor.newInstance(arguments);
  }

  private static Map<String, Object> extractProperties(Object bean) {
    if (bean instanceof Map<?, ?> map) {
      LinkedHashMap<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, value) -> result.put(String.valueOf(key), value));
      return result;
    }
    if (isSimpleValue(bean.getClass())) {
      throw new IllegalArgumentException(
          "Expected a bean/record/map for variable mapping but got " + bean.getClass().getName());
    }
    try {
      LinkedHashMap<String, Object> result = new LinkedHashMap<>();
      if (bean.getClass().isRecord()) {
        for (RecordComponent component : bean.getClass().getRecordComponents()) {
          result.put(component.getName(), component.getAccessor().invoke(bean));
        }
        return result;
      }
      for (PropertyDescriptor descriptor :
          Introspector.getBeanInfo(bean.getClass(), Object.class).getPropertyDescriptors()) {
        if (descriptor.getReadMethod() == null) {
          continue;
        }
        result.put(descriptor.getName(), descriptor.getReadMethod().invoke(bean));
      }
      if (!result.isEmpty()) {
        return result;
      }
    } catch (ReflectiveOperationException | java.beans.IntrospectionException e) {
      throw new IllegalArgumentException(
          "Cannot extract bean properties from " + bean.getClass().getName(), e);
    }
    throw new IllegalArgumentException(
        "No readable bean properties found on " + bean.getClass().getName());
  }

  private static boolean isSimpleValue(Class<?> type) {
    Class<?> boxed = box(type);
    return boxed == String.class
        || Number.class.isAssignableFrom(boxed)
        || boxed == Boolean.class
        || boxed == Character.class
        || boxed == UUID.class
        || boxed == byte[].class
        || boxed.isEnum();
  }

  private static long toLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
  }

  private static double toDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return Double.parseDouble(String.valueOf(value));
  }

  private static boolean toBoolean(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private static Class<?> box(Class<?> type) {
    if (!type.isPrimitive()) {
      return type;
    }
    if (type == long.class) {
      return Long.class;
    }
    if (type == int.class) {
      return Integer.class;
    }
    if (type == short.class) {
      return Short.class;
    }
    if (type == byte.class) {
      return Byte.class;
    }
    if (type == double.class) {
      return Double.class;
    }
    if (type == float.class) {
      return Float.class;
    }
    if (type == boolean.class) {
      return Boolean.class;
    }
    if (type == char.class) {
      return Character.class;
    }
    return type;
  }
}
