/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.variables;

import com.google.protobuf.ByteString;
import io.taktx.proto.VarList;
import io.taktx.proto.VarMap;
import io.taktx.proto.VariableValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory and conversion utilities for {@link VariableValue} proto messages.
 *
 * <p>Replaces the legacy variable-tree conversion helpers at every call site. All methods are
 * null-safe: a Java {@code null} input produces a {@link #nullValue()}.
 *
 * <p>Use {@link #of(Object)} to convert arbitrary Java objects (Map, List, primitives, null) to the
 * proto representation. Use {@link #toJavaObject(VariableValue)} to convert back for FEEL/DMN
 * adapters or REST serialisation.
 */
public final class Variables {

  private Variables() {}

  // ── Typed factories ──────────────────────────────────────────────────────

  /** Encodes a {@code long} as {@code sint64} (zigzag varint). */
  public static VariableValue of(long value) {
    return VariableValue.newBuilder().setLongValue(value).build();
  }

  /** Encodes a {@code String}. A {@code null} string produces {@link #nullValue()}. */
  public static VariableValue of(String value) {
    if (value == null) return nullValue();
    return VariableValue.newBuilder().setStringValue(value).build();
  }

  /** Encodes a {@code double}. */
  public static VariableValue of(double value) {
    return VariableValue.newBuilder().setDoubleValue(value).build();
  }

  /** Encodes a {@code boolean}. */
  public static VariableValue of(boolean value) {
    return VariableValue.newBuilder().setBoolValue(value).build();
  }

  /** Encodes a raw {@code byte[]} blob. A {@code null} input produces {@link #nullValue()}. */
  public static VariableValue of(byte[] value) {
    if (value == null) return nullValue();
    return VariableValue.newBuilder().setBytesValue(ByteString.copyFrom(value)).build();
  }

  /** Returns the canonical proto representation of a JSON/FEEL {@code null}. */
  public static VariableValue nullValue() {
    return VariableValue.newBuilder().setNullValue(true).build();
  }

  // ── Generic factory ──────────────────────────────────────────────────────

  /**
   * Converts an arbitrary Java value to a {@link VariableValue}.
   *
   * <p>Supported types:
   *
   * <ul>
   *   <li>{@code null} → {@link #nullValue()}
   *   <li>{@code Boolean} → {@link #of(boolean)}
   *   <li>{@code Long}, {@code Integer}, {@code Short}, {@code Byte} → {@link #of(long)}
   *   <li>{@code Float}, {@code Double} → {@link #of(double)}
   *   <li>{@code String} → {@link #of(String)}
   *   <li>{@code byte[]} → {@link #of(byte[])}
   *   <li>{@code List<?>} → {@link VarList} with each element converted recursively
   *   <li>{@code Map<String,?>} → {@link VarMap} with each value converted recursively
   * </ul>
   *
   * @throws IllegalArgumentException for unsupported types
   */
  public static VariableValue of(Object javaValue) {
    if (javaValue == null) return nullValue();
    if (javaValue instanceof Boolean b) return of(b.booleanValue());
    if (javaValue instanceof Long l) return of(l.longValue());
    if (javaValue instanceof Integer i) return of((long) i.intValue());
    if (javaValue instanceof Short s) return of((long) s.shortValue());
    if (javaValue instanceof Byte by) return of((long) by.byteValue());
    if (javaValue instanceof Double d) return of(d.doubleValue());
    if (javaValue instanceof Float f) return of((double) f.floatValue());
    if (javaValue instanceof String s) return of(s);
    if (javaValue instanceof byte[] ba) return of(ba);
    if (javaValue instanceof List<?> list) {
      VarList.Builder lb = VarList.newBuilder();
      for (Object item : list) {
        lb.addItems(of(item));
      }
      return VariableValue.newBuilder().setListValue(lb.build()).build();
    }
    if (javaValue instanceof Map<?, ?> map) {
      VarMap.Builder mb = VarMap.newBuilder();
      for (Map.Entry<?, ?> e : map.entrySet()) {
        mb.putEntries(String.valueOf(e.getKey()), of(e.getValue()));
      }
      return VariableValue.newBuilder().setMapValue(mb.build()).build();
    }
    throw new IllegalArgumentException(
        "Cannot convert " + javaValue.getClass().getName() + " to VariableValue");
  }

  // ── Map convenience builders (up to 5 pairs) ────────────────────────────

  /** Builds a {@link VarMap}-backed {@link VariableValue} from a single key-value pair. */
  public static Map<String, VariableValue> map(String k1, Object v1) {
    Map<String, VariableValue> m = new LinkedHashMap<>();
    m.put(k1, of(v1));
    return m;
  }

  /** Builds a {@link VarMap}-backed {@link VariableValue} from two key-value pairs. */
  public static Map<String, VariableValue> map(String k1, Object v1, String k2, Object v2) {
    Map<String, VariableValue> m = new LinkedHashMap<>();
    m.put(k1, of(v1));
    m.put(k2, of(v2));
    return m;
  }

  /** Builds a variable map from three key-value pairs. */
  public static Map<String, VariableValue> map(
      String k1, Object v1, String k2, Object v2, String k3, Object v3) {
    Map<String, VariableValue> m = new LinkedHashMap<>();
    m.put(k1, of(v1));
    m.put(k2, of(v2));
    m.put(k3, of(v3));
    return m;
  }

  /** Builds a variable map from four key-value pairs. */
  public static Map<String, VariableValue> map(
      String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
    Map<String, VariableValue> m = new LinkedHashMap<>();
    m.put(k1, of(v1));
    m.put(k2, of(v2));
    m.put(k3, of(v3));
    m.put(k4, of(v4));
    return m;
  }

  /** Builds a variable map from five key-value pairs. */
  public static Map<String, VariableValue> map(
      String k1,
      Object v1,
      String k2,
      Object v2,
      String k3,
      Object v3,
      String k4,
      Object v4,
      String k5,
      Object v5) {
    Map<String, VariableValue> m = new LinkedHashMap<>();
    m.put(k1, of(v1));
    m.put(k2, of(v2));
    m.put(k3, of(v3));
    m.put(k4, of(v4));
    m.put(k5, of(v5));
    return m;
  }

  // ── Conversion back to plain Java (for FEEL/DMN adapters) ───────────────

  /**
   * Converts a {@link VariableValue} to the closest plain-Java equivalent.
   *
   * <ul>
   *   <li>{@code null_value} / unset → {@code null}
   *   <li>{@code bool_value} → {@link Boolean}
   *   <li>{@code long_value} → {@link Long}
   *   <li>{@code double_value} → {@link Double}
   *   <li>{@code string_value} → {@link String}
   *   <li>{@code bytes_value} → {@code byte[]}
   *   <li>{@code map_value} → {@link Map}{@code <String, Object>}
   *   <li>{@code list_value} → {@link List}{@code <Object>}
   * </ul>
   *
   * @param value the proto value; {@code null} input returns {@code null}
   */
  public static Object toJavaObject(VariableValue value) {
    if (value == null) return null;
    return switch (value.getKindCase()) {
      case NULL_VALUE -> null;
      case BOOL_VALUE -> value.getBoolValue();
      case LONG_VALUE -> value.getLongValue();
      case DOUBLE_VALUE -> value.getDoubleValue();
      case STRING_VALUE -> value.getStringValue();
      case BYTES_VALUE -> value.getBytesValue().toByteArray();
      case MAP_VALUE -> toJavaMap(value.getMapValue());
      case LIST_VALUE -> toJavaList(value.getListValue());
      case KIND_NOT_SET -> null;
    };
  }

  /**
   * Converts a {@link VarMap} to a plain {@link Map}{@code <String, Object>}.
   *
   * @param varMap the proto map; {@code null} input returns an empty map
   */
  public static Map<String, Object> toJavaMap(VarMap varMap) {
    if (varMap == null) return new LinkedHashMap<>();
    Map<String, Object> result = new LinkedHashMap<>(varMap.getEntriesCount());
    varMap.getEntriesMap().forEach((k, v) -> result.put(k, toJavaObject(v)));
    return result;
  }

  /**
   * Converts a {@link VarList} to a plain {@link List}{@code <Object>}.
   *
   * @param varList the proto list; {@code null} input returns an empty list
   */
  public static List<Object> toJavaList(VarList varList) {
    if (varList == null) return new ArrayList<>();
    List<Object> result = new ArrayList<>(varList.getItemsCount());
    varList.getItemsList().forEach(v -> result.add(toJavaObject(v)));
    return result;
  }

  // ── VarMap builder helper ────────────────────────────────────────────────

  /**
   * Wraps a {@link Map}{@code <String, VariableValue>} into a {@link VarMap} proto message. Useful
   * when constructing nested map values.
   */
  public static VarMap toVarMap(Map<String, VariableValue> entries) {
    VarMap.Builder b = VarMap.newBuilder();
    entries.forEach(b::putEntries);
    return b.build();
  }
}
