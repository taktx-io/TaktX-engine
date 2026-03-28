/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TopicSegmentValidatorTest {

  // ── Valid values ────────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(
      strings = {
        "default",
        "my-namespace",
        "my_namespace",
        "Namespace1",
        "acme-corp",
        "ACME",
        "tenant123",
        "a",
        "A1-b2_C3"
      })
  void validSegments_doNotThrow(String value) {
    assertThatCode(() -> TopicSegmentValidator.validate("namespace", value))
        .doesNotThrowAnyException();
  }

  @Test
  void maxLengthSegment_doesNotThrow() {
    String exactly63 = "a".repeat(TopicSegmentValidator.MAX_SEGMENT_LENGTH);
    assertThatCode(() -> TopicSegmentValidator.validate("namespace", exactly63))
        .doesNotThrowAnyException();
  }

  // ── Blank / null ────────────────────────────────────────────────────────────

  @Test
  void nullValue_throwsWithSegmentName() {
    String nullValue = null;
    assertThatThrownBy(() -> TopicSegmentValidator.validate("tenant-id", nullValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant-id")
        .hasMessageContaining("blank");
  }

  @Test
  void emptyValue_throws() {
    assertThatThrownBy(() -> TopicSegmentValidator.validate("namespace", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void blankValue_throws() {
    assertThatThrownBy(() -> TopicSegmentValidator.validate("namespace", "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  // ── Too long ────────────────────────────────────────────────────────────────

  @Test
  void segmentExceedingMaxLength_throws() {
    String tooLong = "a".repeat(TopicSegmentValidator.MAX_SEGMENT_LENGTH + 1);
    assertThatThrownBy(() -> TopicSegmentValidator.validate("tenant-id", tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant-id")
        .hasMessageContaining(String.valueOf(TopicSegmentValidator.MAX_SEGMENT_LENGTH));
  }

  // ── Illegal characters ──────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(
      strings = {
        "name.space", // dot is the delimiter — must be rejected
        "name space", // space
        "name/space", // slash
        "name\\space", // backslash
        "name:space", // colon
        "name@space", // at-sign
        "name#space", // hash
        "name$space", // dollar
        "name!space", // exclamation
        "name*space", // asterisk
        "name+space", // plus
        "name=space", // equals
        "name,space", // comma
        "name;space", // semicolon
        "name<space", // less-than
        "name>space", // greater-than
        "name\"space", // double-quote
        "name'space", // single-quote
        "name`space", // backtick
        "name~space", // tilde
        "name%space", // percent
        "name^space", // caret
        "name&space", // ampersand
        "name|space", // pipe
        "name?space", // question mark
        "name[space", // bracket
        "name{space", // brace
        "tenant.id.with.dots"
      })
  void illegalCharacters_throw(String value) {
    assertThatThrownBy(() -> TopicSegmentValidator.validate("namespace", value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("illegal characters");
  }
}
