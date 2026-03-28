/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.bpmn.TExtensionElements;
import java.util.Optional;

public class ExtensionElementHelper {
  private ExtensionElementHelper() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static <T> Optional<T> extractExtensionElement(
      TExtensionElements extensionElements, Class<T> clazz) {
    return extensionElements != null
        ? extensionElements.getAny().stream().filter(clazz::isInstance).findFirst().map(clazz::cast)
        : Optional.empty();
  }
}
