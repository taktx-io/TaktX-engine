/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.generic;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@NoArgsConstructor
@AllArgsConstructor
public class TenantNamespaceNameWrapper {

  @ConfigProperty(name = "takt.engine.tenant")
  String tenant;

  @ConfigProperty(name = "takt.engine.namespace")
  String namespace;

  public String getPrefixed(String name) {
    String prefixedName = tenant + "." + namespace + "." + name;
    if (prefixedName.length() > 100) {
      throw new IllegalArgumentException("Topic name is too long: " + prefixedName);
    }
    return prefixedName;
  }
}
