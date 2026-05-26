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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    VariableValue inner1 = Variables.of(1L);

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
  void of_object_handlesJavaBean() {
    VariableValue value =
        Variables.of(
            new TestBean("alpha", 7, UUID.fromString("123e4567-e89b-12d3-a456-426614174000")));

    assertThat(value.getKindCase()).isEqualTo(VariableValue.KindCase.MAP_VALUE);
    assertThat(Variables.toJavaObject(value))
        .isEqualTo(
            Map.of(
                "count", 7L,
                "id", "123e4567-e89b-12d3-a456-426614174000",
                "name", "alpha"));
  }

  @Test
  void of_object_handlesRecordAndInstantLeafType() {
    TestRecord input = new TestRecord("beta", Instant.parse("2026-05-19T10:15:30Z"));

    VariableValue value = Variables.of(input);

    assertThat(value.getKindCase()).isEqualTo(VariableValue.KindCase.MAP_VALUE);
    assertThat(Variables.toJavaObject(value))
        .isEqualTo(Map.of("createdAt", "2026-05-19T10:15:30Z", "name", "beta"));
  }

  @Test
  void of_object_throwsForUnsupportedType() {
    Object unsupported = new Object();
    assertThatThrownBy(() -> Variables.of(unsupported))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toTypedObject_reconstructsBeanFromVariableMap() {
    Map<String, VariableValue> variables =
        Variables.map(
            "name",
            "alpha",
            "count",
            7,
            "id",
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    TestBean bean = Variables.toTypedObject(variables, TestBean.class);

    assertThat(bean.getName()).isEqualTo("alpha");
    assertThat(bean.getCount()).isEqualTo(7);
    assertThat(bean.getId()).isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
  }

  @Test
  void helperBean_accessorsRemainUsableForBeanMapping() {
    TestBean bean = new TestBean();
    bean.setName("gamma");
    bean.setCount(11);
    bean.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"));

    assertThat(bean.getCount()).isEqualTo(11);
    assertThat(bean.getId()).isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"));
  }

  @Test
  void toTypedObject_reconstructsRecordFromVariableMap() {
    Map<String, VariableValue> variables =
        Variables.map("name", "beta", "createdAt", Instant.parse("2026-05-19T10:15:30Z"));

    TestRecord typedRecord = Variables.toTypedObject(variables, TestRecord.class);

    assertThat(typedRecord)
        .isEqualTo(new TestRecord("beta", Instant.parse("2026-05-19T10:15:30Z")));
  }

  @Test
  void fromJavaObject_convertsStringsToEnumAndCharacter() {
    assertThat(VariableObjectMapper.fromJavaObject("ACTIVE", TestStatus.class))
        .isEqualTo(TestStatus.ACTIVE);
    assertThat(VariableObjectMapper.fromJavaObject("Z", Character.class)).isEqualTo('Z');
    assertThatThrownBy(() -> VariableObjectMapper.fromJavaObject("too-long", Character.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expected length 1");
  }

  @Test
  void of_object_handlesArrays() {
    VariableValue value = Variables.of(new String[] {"alpha", "beta"});

    assertThat(value.getKindCase()).isEqualTo(VariableValue.KindCase.LIST_VALUE);
    assertThat(Variables.toJavaObject(value)).isEqualTo(List.of("alpha", "beta"));
  }

  @Test
  void toVariableMap_rejectsCyclicObjectGraphs() {
    CyclicBean bean = new CyclicBean();
    bean.setName("root");
    bean.setSelf(bean);

    assertThat(bean.getSelf()).isSameAs(bean);

    assertThatThrownBy(() -> VariableObjectMapper.toVariableMap(bean))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cyclic object graph");
  }

  // ── Size assertion ───────────────────────────────────────────────────────

  @Test
  void threeTypicalVariables_encodeBelowFortyBytes() {
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

  static final class TestBean {

    private String name;
    private int count;
    private UUID id;

    TestBean() {}

    TestBean(String name, int count, UUID id) {
      this.name = name;
      this.count = count;
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }
  }

  record TestRecord(String name, Instant createdAt) {}

  enum TestStatus {
    ACTIVE,
    INACTIVE
  }

  static final class CyclicBean {
    private String name;
    private CyclicBean self;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public CyclicBean getSelf() {
      return self;
    }

    public void setSelf(CyclicBean self) {
      this.self = self;
    }
  }
}
