/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi;

import jakarta.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and normalises {@code businessKey} and {@code tags} supplied on process start.
 *
 * <p>These fields are purely operational metadata and must not influence execution semantics.
 */
public final class BusinessMetadataValidator {

  static final int MAX_BUSINESS_KEY_LENGTH = 512;
  static final int MAX_TAG_COUNT = 20;
  static final int MAX_TAG_LENGTH = 64;

  /** Allowed characters for a single tag: {@code a-z 0-9 . _ -} */
  private static final Pattern VALID_TAG_PATTERN = Pattern.compile("[a-z0-9._-]+");

  private BusinessMetadataValidator() {}

  /**
   * Normalises {@code businessKey}: trims whitespace and converts an empty result to {@code null}.
   *
   * @throws IllegalArgumentException if the trimmed value exceeds {@value #MAX_BUSINESS_KEY_LENGTH}
   *     characters.
   */
  @Nullable
  public static String validateBusinessKey(@Nullable String businessKey) {
    if (businessKey == null) {
      return null;
    }
    String trimmed = businessKey.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > MAX_BUSINESS_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "businessKey exceeds maximum length of "
              + MAX_BUSINESS_KEY_LENGTH
              + " characters (got "
              + trimmed.length()
              + ")");
    }
    return trimmed;
  }

  /**
   * Normalises {@code tags}: trims each value, converts to lowercase, removes duplicates, and
   * validates the allowed character set.
   *
   * @throws IllegalArgumentException if any tag is empty after trimming, contains illegal
   *     characters, exceeds {@value #MAX_TAG_LENGTH} characters, or the set exceeds {@value
   *     #MAX_TAG_COUNT} entries.
   */
  public static Set<String> validateTags(@Nullable Set<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return Set.of();
    }
    // Use LinkedHashSet to preserve insertion order while deduplicating.
    Set<String> normalised = new LinkedHashSet<>();
    for (String raw : tags) {
      if (raw == null) {
        throw new IllegalArgumentException("tags must not contain null values");
      }
      String tag = raw.trim().toLowerCase();
      if (tag.isEmpty()) {
        throw new IllegalArgumentException(
            "tags must not contain empty values (raw value: '" + raw + "')");
      }
      if (tag.length() > MAX_TAG_LENGTH) {
        throw new IllegalArgumentException(
            "tag '"
                + tag
                + "' exceeds maximum length of "
                + MAX_TAG_LENGTH
                + " characters (got "
                + tag.length()
                + ")");
      }
      if (!VALID_TAG_PATTERN.matcher(tag).matches()) {
        throw new IllegalArgumentException(
            "tag '"
                + tag
                + "' contains illegal characters; only a-z, 0-9, '.', '_', '-' are allowed");
      }
      normalised.add(tag);
    }
    if (normalised.size() > MAX_TAG_COUNT) {
      throw new IllegalArgumentException(
          "too many tags: maximum is "
              + MAX_TAG_COUNT
              + ", got "
              + normalised.size()
              + " after deduplication");
    }
    return Set.copyOf(normalised);
  }
}
