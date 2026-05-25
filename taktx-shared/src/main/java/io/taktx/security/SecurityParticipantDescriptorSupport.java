/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.SecurityParticipantDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Shared normalization and validation support for {@link SecurityParticipantDescriptor}. */
public final class SecurityParticipantDescriptorSupport {

  private SecurityParticipantDescriptorSupport() {}

  public static SecurityParticipantDescriptor normalize(SecurityParticipantDescriptor descriptor) {
    if (descriptor == null) {
      return null;
    }
    return new SecurityParticipantDescriptor(
        normalizeRequiredString(descriptor.participantId()),
        descriptor.kind(),
        normalizeSet(descriptor.capabilities()),
        normalizeOptionalString(descriptor.componentType()));
  }

  public static List<String> validationErrors(SecurityParticipantDescriptor descriptor) {
    List<String> errors = new ArrayList<>();
    if (descriptor == null) {
      errors.add("descriptor must not be null");
      return List.copyOf(errors);
    }

    SecurityParticipantDescriptor normalized = normalize(descriptor);
    if (isBlank(normalized.participantId())) {
      errors.add("participantId must not be blank");
    }
    if (normalized.kind() == null) {
      errors.add("kind must not be null");
    }
    if (containsNull(normalized.capabilities())) {
      errors.add("capabilities must not contain null values");
    }
    if (normalized.capabilities().isEmpty()) {
      errors.add("capabilities must not be empty");
    }
    return List.copyOf(errors);
  }

  public static SecurityParticipantDescriptor requireValid(
      SecurityParticipantDescriptor descriptor) {
    List<String> errors = validationErrors(descriptor);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join("; ", errors));
    }
    return normalize(descriptor);
  }

  private static String normalizeRequiredString(String value) {
    return value == null ? null : value.trim();
  }

  private static String normalizeOptionalString(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private static <T> Set<T> normalizeSet(Set<T> values) {
    if (values == null || values.isEmpty()) {
      return Set.of();
    }
    return Collections.unmodifiableSet(new LinkedHashSet<>(values));
  }

  private static boolean containsNull(Set<?> values) {
    return values != null && values.stream().anyMatch(Objects::isNull);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
