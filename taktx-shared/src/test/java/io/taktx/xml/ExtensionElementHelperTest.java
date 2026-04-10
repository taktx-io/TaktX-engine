/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taktx.bpmn.TExtensionElements;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExtensionElementHelperTest {

  @Test
  void extractExtensionElement_returnsEmpty_whenExtensionElementsIsNull() {
    Optional<String> result = ExtensionElementHelper.extractExtensionElement(null, String.class);
    assertThat(result).isEmpty();
  }

  @Test
  void extractExtensionElement_returnsEmpty_whenNoMatchingType() {
    TExtensionElements extensions = mock(TExtensionElements.class);
    List<Object> elements = new ArrayList<>();
    elements.add("hello");
    elements.add(42);
    when(extensions.getAny()).thenReturn(elements);

    Optional<Double> result =
        ExtensionElementHelper.extractExtensionElement(extensions, Double.class);

    assertThat(result).isEmpty();
  }

  @Test
  void extractExtensionElement_returnsFirst_whenMatchFound() {
    TExtensionElements extensions = mock(TExtensionElements.class);
    List<Object> elements = new ArrayList<>();
    elements.add("first-string");
    elements.add("second-string");
    elements.add(42);
    when(extensions.getAny()).thenReturn(elements);

    Optional<String> result =
        ExtensionElementHelper.extractExtensionElement(extensions, String.class);

    assertThat(result).hasValue("first-string");
  }
}
