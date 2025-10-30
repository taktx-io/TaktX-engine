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
import io.taktx.dto.CancelCorrelationMessageSubscriptionDTO;
import io.taktx.dto.CancelDefinitionMessageSubscriptionDTO;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.DefinitionMessageSubscriptionDTO;

public class MessageEventTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case DefinitionMessageSubscriptionDTO ignored -> "D";
      case CancelDefinitionMessageSubscriptionDTO ignored -> "C";
      case CorrelationMessageSubscriptionDTO ignored -> "O";
      case CancelCorrelationMessageSubscriptionDTO ignored -> "A";
      case DefinitionMessageEventTriggerDTO ignored -> "E";
      case CorrelationMessageEventTriggerDTO ignored -> "R";
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
      case "D" -> context.constructType(DefinitionMessageSubscriptionDTO.class);
      case "C" -> context.constructType(CancelDefinitionMessageSubscriptionDTO.class);
      case "O" -> context.constructType(CorrelationMessageSubscriptionDTO.class);
      case "A" -> context.constructType(CancelCorrelationMessageSubscriptionDTO.class);
      case "E" -> context.constructType(DefinitionMessageEventTriggerDTO.class);
      case "R" -> context.constructType(CorrelationMessageEventTriggerDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
