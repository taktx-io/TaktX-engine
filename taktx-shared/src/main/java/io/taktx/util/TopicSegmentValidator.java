/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import java.util.regex.Pattern;

/**
 * Validates tenant-id and namespace segment values before they are assembled into Kafka topic
 * names.
 *
 * <h3>Rules</h3>
 *
 * <ul>
 *   <li>Must not be blank.
 *   <li>Only {@code [a-zA-Z0-9_-]} is allowed — the same characters Kafka permits in topic names,
 *       minus the dot ({@code .}) which is TaktX's own segment delimiter.
 *   <li>Maximum 63 characters per segment, leaving room for the namespace, the base topic name, and
 *       the two delimiter dots within Kafka's 249-character topic-name limit.
 * </ul>
 */
public final class TopicSegmentValidator {

  /** Maximum allowed length for a single segment (tenant-id or namespace). */
  public static final int MAX_SEGMENT_LENGTH = 63;

  /**
   * Allowed characters: letters, digits, hyphens and underscores. Dots are intentionally excluded
   * because they are used as the segment delimiter.
   */
  private static final Pattern ALLOWED = Pattern.compile("[a-zA-Z0-9_-]+");

  private TopicSegmentValidator() {}

  /**
   * Validates {@code value} as a topic segment (tenant-id or namespace).
   *
   * @param segmentName human-readable label used in the error message (e.g. {@code "tenant-id"})
   * @param value the value to validate
   * @throws IllegalArgumentException if the value is blank, too long, or contains illegal
   *     characters
   */
  public static void validate(String segmentName, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("taktx.engine." + segmentName + " must not be blank");
    }
    if (value.length() > MAX_SEGMENT_LENGTH) {
      throw new IllegalArgumentException(
          "taktx.engine."
              + segmentName
              + " must be at most "
              + MAX_SEGMENT_LENGTH
              + " characters, got "
              + value.length()
              + ": \""
              + value
              + "\"");
    }
    if (!ALLOWED.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "taktx.engine."
              + segmentName
              + " contains illegal characters. Only [a-zA-Z0-9_-] is allowed (dots are"
              + " reserved as segment delimiters): \""
              + value
              + "\"");
    }
  }
}
