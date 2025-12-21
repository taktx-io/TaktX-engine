/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
