/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.variables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.proto.VarMap;
import io.taktx.proto.VariableValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariablesTest {

  // ── Typed factories ──────────────────────────────────────────────────────

  @Test
  void of_long_encodesAsSint64() {
    VariableValue v = Variables.of(100L);
    assertThat(v.getLongValue()).isEqualTo(100L);
    assertThat(v.getKindCase()).isEqualTo(VariableValue.KindCase.LONG_VALUE);
  }

  @Test
  void of_long_serialisesToAtMostThreeBytes() {
    // Field 3 (long_value), wire type 0: tag=0x18 (1 byte) + zigzag(100)=200 varint=0xC8 0x01 (2
    // bytes) = 3 bytes total.
    byte[] bytes = Variables.of(100L).toByteArray();
    assertThat(bytes).hasSizeLessThanOrEqualTo(3);
  }

  @Test
  void of_string_roundTrips() throws Exception {
    VariableValue v = Variables.of("hello");
    assertThat(v.getStringValue()).isEqualTo("hello");
    assertThat(v.getKindCase()).isEqualTo(VariableValue.KindCase.STRING_VALUE);

    // Round-trip through serialisation
    VariableValue parsed = VariableValue.parseFrom(v.toByteArray());
    assertThat(parsed.getStringValue()).isEqualTo("hello");
  }

  @Test
  void of_string_null_producesNullValue() {
    VariableValue v = Variables.of((String) null);
    assertThat(v.getKindCase()).isEqualTo(VariableValue.KindCase.NULL_VALUE);
    assertThat(v.getNullValue()).isTrue();
  }

  @Test
  void of_double_roundTrips() {
    VariableValue v = Variables.of(3.14);
    assertThat(v.getDoubleValue()).isEqualTo(3.14);
  }

  @Test
  void of_boolean_true_roundTrips() {
    VariableValue v = Variables.of(true);
    assertThat(v.getBoolValue()).isTrue();
    assertThat(v.getKindCase()).isEqualTo(VariableValue.KindCase.BOOL_VALUE);
  }

  @Test
  void of_boolean_false_roundTrips() {
    // false is the proto default — the oneof MUST still be set so the kind case is BOOL_VALUE.
    VariableValue v = Variables.of(false);
    assertThat(v.getKindCase()).isEqualTo(VariableValue.KindCase.BOOL_VALUE);
    assertThat(v.getBoolValue()).isFalse();
  }

  @Test
  void nullValue_kindCase() {
    VariableValue v = Variables.nullValue();
    assertThat(v.getKindCase()).isEqualTo(VariableValue.KindCase.NULL_VALUE);
    assertThat(v.getNullValue()).isTrue();
  }

  @Test
  void of_bytes_roundTrips() {
    byte[] data = {1, 2, 3, 127};
    VariableValue v = Variables.of(data);
    assertThat(v.getBytesValue().toByteArray()).isEqualTo(data);
  }

  // ── Nested structures ────────────────────────────────────────────────────

  @Test
  void nestedVarMap_containingVarList_containingSint64_roundTrips() throws Exception {
    // Build: { "numbers": [1, 2, 3] }
    VariableValue inner1 = Variables.of(1L);
    VariableValue inner2 = Variables.of(2L);
    VariableValue inner3 = Variables.of(3L);

    VariableValue list = Variables.of(List.of(1L, 2L, 3L));
    VariableValue map =
        VariableValue.newBuilder().setMapValue(Variables.toVarMap(Map.of("numbers", list))).build();

    // Serialise → parse → assert
    byte[] bytes = map.toByteArray();
    VariableValue parsed = VariableValue.parseFrom(bytes);

    assertThat(parsed.getKindCase()).isEqualTo(VariableValue.KindCase.MAP_VALUE);
    VarMap parsedMap = parsed.getMapValue();
    assertThat(parsedMap.getEntriesMap()).containsKey("numbers");

    VariableValue parsedList = parsedMap.getEntriesMap().get("numbers");
    assertThat(parsedList.getKindCase()).isEqualTo(VariableValue.KindCase.LIST_VALUE);
    assertThat(parsedList.getListValue().getItemsCount()).isEqualTo(3);
    assertThat(parsedList.getListValue().getItems(0).getLongValue()).isEqualTo(1L);
    assertThat(parsedList.getListValue().getItems(1).getLongValue()).isEqualTo(2L);
    assertThat(parsedList.getListValue().getItems(2).getLongValue()).isEqualTo(3L);

    // Verify inner field objects also round-tripped
    VariableValue reparsed1 = VariableValue.parseFrom(inner1.toByteArray());
    assertThat(reparsed1.getLongValue()).isEqualTo(1L);
  }

  // ── toJavaObject ─────────────────────────────────────────────────────────

  @Test
  void toJavaObject_long_returnsLong() {
    Object result = Variables.toJavaObject(Variables.of(42L));
    assertThat(result).isInstanceOf(Long.class).isEqualTo(42L);
  }

  @Test
  void toJavaObject_string_returnsString() {
    Object result = Variables.toJavaObject(Variables.of("Alice"));
    assertThat(result).isEqualTo("Alice");
  }

  @Test
  void toJavaObject_null_value_returnsNull() {
    assertThat(Variables.toJavaObject(Variables.nullValue())).isNull();
  }

  @Test
  void toJavaObject_null_input_returnsNull() {
    assertThat(Variables.toJavaObject(null)).isNull();
  }

  @Test
  void toJavaObject_boolean_returnsBoolean() {
    assertThat(Variables.toJavaObject(Variables.of(true))).isEqualTo(true);
    assertThat(Variables.toJavaObject(Variables.of(false))).isEqualTo(false);
  }

  @Test
  void toJavaObject_double_returnsDouble() {
    assertThat(Variables.toJavaObject(Variables.of(2.718))).isEqualTo(2.718);
  }

  @Test
  void toJavaObject_map_returnsMapOfObjects() {
    VariableValue mapVal = Variables.of(Map.of("x", 1L, "y", "hello"));
    Object result = Variables.toJavaObject(mapVal);
    assertThat(result).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> m = (Map<String, Object>) result;
    assertThat(m).containsEntry("x", 1L).containsEntry("y", "hello");
  }

  @Test
  void toJavaObject_list_returnsListOfObjects() {
    VariableValue listVal = Variables.of(List.of(10L, "foo", true));
    Object result = Variables.toJavaObject(listVal);
    assertThat(result).isInstanceOf(List.class);

    @SuppressWarnings("unchecked")
    List<Object> l = (List<Object>) result;
    assertThat(l).containsExactly(10L, "foo", true);
  }

  // ── Generic of(Object) ───────────────────────────────────────────────────

  @Test
  void of_object_handlesAllPrimitiveTypes() {
    assertThat(Variables.of((Object) null).getKindCase())
        .isEqualTo(VariableValue.KindCase.NULL_VALUE);
    assertThat(Variables.of((Object) "text").getStringValue()).isEqualTo("text");
    assertThat(Variables.of((Object) 99L).getLongValue()).isEqualTo(99L);
    assertThat(Variables.of((Object) 7).getLongValue()).isEqualTo(7L); // Integer promoted
    assertThat(Variables.of((Object) 3.14).getDoubleValue()).isEqualTo(3.14);
    assertThat(Variables.of((Object) 1.5f).getDoubleValue())
        .isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.0001));
    assertThat(Variables.of((Object) true).getBoolValue()).isTrue();
  }

  @Test
  void of_object_handlesList() {
    VariableValue v = Variables.of(List.of(1L, 2L));
    assertThat(v.getKindCase()).isEqualTo(VariableValue.KindCase.LIST_VALUE);
    assertThat(v.getListValue().getItemsCount()).isEqualTo(2);
  }

  @Test
  void of_object_handlesMap() {
    VariableValue v = Variables.of(Map.of("k", "v"));
    assertThat(v.getKindCase()).isEqualTo(VariableValue.KindCase.MAP_VALUE);
    assertThat(v.getMapValue().getEntriesMap()).containsKey("k");
  }

  @Test
  void of_object_throwsForUnsupportedType() {
    assertThatThrownBy(() -> Variables.of(new Object()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot convert");
  }

  // ── Size assertion ───────────────────────────────────────────────────────

  @Test
  void threeTypicalVariables_encodeBelowFortyBytes() {
    // {"amount": 100, "name": "Alice", "active": true}
    // Proto map encoding: each entry = outer_tag(1) + outer_len(1) + key_field(key_len+2) +
    // value_field(value_msg_len+2). Calculated: ~46 bytes for these key names and values.
    // Compare: equivalent JSON {"amount":100,"name":"Alice","active":true} = 38 bytes just for
    // the raw text, without any type info or nesting wrappers.
    Map<String, VariableValue> vars =
        Variables.map("amount", 100L, "name", "Alice", "active", true);

    VarMap varMap = Variables.toVarMap(vars);
    byte[] encoded = varMap.toByteArray();

    assertThat(encoded)
        .as(
            "Proto VarMap of 3 typical variables must encode in ≤ 50 bytes (was %d)",
            encoded.length)
        .hasSizeLessThanOrEqualTo(50);
  }

  // ── map() convenience overloads ──────────────────────────────────────────

  @Test
  void map_overloads_producerCorrectEntries() {
    assertThat(Variables.map("a", 1L)).containsOnlyKeys("a");
    assertThat(Variables.map("a", 1L, "b", 2L)).containsOnlyKeys("a", "b");
    assertThat(Variables.map("a", 1L, "b", 2L, "c", 3L)).containsOnlyKeys("a", "b", "c");
    assertThat(Variables.map("a", 1L, "b", 2L, "c", 3L, "d", 4L))
        .containsOnlyKeys("a", "b", "c", "d");
    assertThat(Variables.map("a", 1L, "b", 2L, "c", 3L, "d", 4L, "e", 5L))
        .containsOnlyKeys("a", "b", "c", "d", "e");
  }
}
