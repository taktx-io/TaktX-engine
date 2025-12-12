/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;

public class VoidResultProcessor implements ResultProcessor {

  @Override
  public Object process(Object object) {
    return new HashMap<String, JsonNode>();
  }
}
