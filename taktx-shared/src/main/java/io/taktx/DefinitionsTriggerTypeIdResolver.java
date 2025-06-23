/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDefinitionActivationDTO;
import io.taktx.dto.XmlDefinitionsDTO;

public class DefinitionsTriggerTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case XmlDefinitionsDTO ignored -> "X";
      case ParsedDefinitionsDTO ignored -> "P";
      case ProcessDefinitionActivationDTO ignored -> "A";
      default -> throw new IllegalStateException("Unknown type: " + value.getClass());
    };
  }

  @Override
  public String idFromValueAndType(Object o, Class<?> suggestedType) {
    return idFromValue(o);
  }

  @Override
  public JsonTypeInfo.Id getMechanism() {
    return JsonTypeInfo.Id.CUSTOM;
  }

  @Override
  public JavaType typeFromId(DatabindContext context, String id) {
    return switch (id) {
      case "X" -> context.constructType(XmlDefinitionsDTO.class);
      case "P" -> context.constructType(ParsedDefinitionsDTO.class);
      case "A" -> context.constructType(ProcessDefinitionActivationDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
