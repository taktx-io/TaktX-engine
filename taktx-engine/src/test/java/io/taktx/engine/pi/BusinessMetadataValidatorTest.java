/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BusinessMetadataValidatorTest {

  // ── businessKey tests ────────────────────────────────────────────────────

  @Test
  void businessKey_null_returnsNull() {
    assertThat(BusinessMetadataValidator.validateBusinessKey(null)).isNull();
  }

  @Test
  void businessKey_emptyString_returnsNull() {
    assertThat(BusinessMetadataValidator.validateBusinessKey("")).isNull();
  }

  @Test
  void businessKey_whitespaceOnly_returnsNull() {
    assertThat(BusinessMetadataValidator.validateBusinessKey("   ")).isNull();
  }

  @Test
  void businessKey_trimmed() {
    assertThat(BusinessMetadataValidator.validateBusinessKey("  ORDER-1234  "))
        .isEqualTo("ORDER-1234");
  }

  @Test
  void businessKey_exactMaxLength_accepted() {
    String key = "a".repeat(BusinessMetadataValidator.MAX_BUSINESS_KEY_LENGTH);
    assertThat(BusinessMetadataValidator.validateBusinessKey(key)).isEqualTo(key);
  }

  @Test
  void businessKey_exceedsMaxLength_throws() {
    String tooLong = "a".repeat(BusinessMetadataValidator.MAX_BUSINESS_KEY_LENGTH + 1);
    assertThatThrownBy(() -> BusinessMetadataValidator.validateBusinessKey(tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("businessKey exceeds maximum length");
  }

  @Test
  void businessKey_trimmedLengthExceedsMax_throws() {
    // The trimmed value (513 chars) exceeds MAX_BUSINESS_KEY_LENGTH (512)
    String borderlineWithSpaces =
        " " + "a".repeat(BusinessMetadataValidator.MAX_BUSINESS_KEY_LENGTH + 1) + " ";
    assertThatThrownBy(() -> BusinessMetadataValidator.validateBusinessKey(borderlineWithSpaces))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("businessKey exceeds maximum length");
  }

  // ── tags tests ───────────────────────────────────────────────────────────

  @Test
  void tags_null_returnsEmptySet() {
    assertThat(BusinessMetadataValidator.validateTags(null)).isEmpty();
  }

  @Test
  void tags_emptySet_returnsEmptySet() {
    assertThat(BusinessMetadataValidator.validateTags(Set.of())).isEmpty();
  }

  @Test
  void tags_normalised_lowercaseAndTrimmed() {
    Set<String> result = BusinessMetadataValidator.validateTags(Set.of("  Hello  ", "WORLD"));
    assertThat(result).containsExactlyInAnyOrder("hello", "world");
  }

  @Test
  void tags_duplicatesRemoved() {
    Set<String> input = new LinkedHashSet<>();
    input.add("foo");
    input.add("FOO");
    input.add("foo");
    Set<String> result = BusinessMetadataValidator.validateTags(input);
    assertThat(result).containsExactly("foo");
  }

  @Test
  void tags_allowedCharacters_accepted() {
    Set<String> result = BusinessMetadataValidator.validateTags(Set.of("abc-123", "x.y_z", "0-9"));
    assertThat(result).containsExactlyInAnyOrder("abc-123", "x.y_z", "0-9");
  }

  @Test
  void tags_emptyValueAfterTrim_throws() {
    assertThatThrownBy(() -> BusinessMetadataValidator.validateTags(Set.of("  ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty values");
  }

  @Test
  void tags_illegalCharacters_throws() {
    assertThatThrownBy(() -> BusinessMetadataValidator.validateTags(Set.of("has space")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("illegal characters");
  }

  @Test
  void tags_illegalSpecialChar_throws() {
    assertThatThrownBy(() -> BusinessMetadataValidator.validateTags(Set.of("bad#tag")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("illegal characters");
  }

  @Test
  void tags_exactMaxTagLength_accepted() {
    String maxTag = "a".repeat(BusinessMetadataValidator.MAX_TAG_LENGTH);
    Set<String> result = BusinessMetadataValidator.validateTags(Set.of(maxTag));
    assertThat(result).containsExactly(maxTag);
  }

  @Test
  void tags_exceedsMaxTagLength_throws() {
    String tooLong = "a".repeat(BusinessMetadataValidator.MAX_TAG_LENGTH + 1);
    assertThatThrownBy(() -> BusinessMetadataValidator.validateTags(Set.of(tooLong)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds maximum length");
  }

  @Test
  void tags_tooManyTags_throws() {
    Set<String> tooMany = new LinkedHashSet<>();
    for (int i = 0; i <= BusinessMetadataValidator.MAX_TAG_COUNT; i++) {
      tooMany.add("tag" + i);
    }
    assertThatThrownBy(() -> BusinessMetadataValidator.validateTags(tooMany))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too many tags");
  }

  @Test
  void tags_exactMaxTagCount_accepted() {
    Set<String> maxTags = new LinkedHashSet<>();
    for (int i = 0; i < BusinessMetadataValidator.MAX_TAG_COUNT; i++) {
      maxTags.add("tag" + i);
    }
    Set<String> result = BusinessMetadataValidator.validateTags(maxTags);
    assertThat(result).hasSize(BusinessMetadataValidator.MAX_TAG_COUNT);
  }

  @Test
  void tags_nullValueInSet_throws() {
    Set<String> withNull = new LinkedHashSet<>();
    withNull.add(null);
    assertThatThrownBy(() -> BusinessMetadataValidator.validateTags(withNull))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null values");
  }

  @Test
  void tags_resultIsImmutable() {
    Set<String> result = BusinessMetadataValidator.validateTags(Set.of("foo"));
    assertThatThrownBy(() -> result.add("bar")).isInstanceOf(UnsupportedOperationException.class);
  }
}
