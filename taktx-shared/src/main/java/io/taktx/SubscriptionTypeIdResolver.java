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
import io.taktx.dto.subscriptions.BoundaryEventCatchAllErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.BoundaryEventCatchAllEscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.BoundaryEventErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.BoundaryEventEscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.EventSubProcessCatchAllErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.EventSubProcessCatchAllEscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.EventSubProcessErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.EventSubProcessEscalationSubscriptionDTO;

public class SubscriptionTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case BoundaryEventCatchAllErrorSubscriptionDTO ignored -> "A";
      case BoundaryEventErrorSubscriptionDTO ignored -> "B";
      case BoundaryEventCatchAllEscalationSubscriptionDTO ignored -> "C";
      case BoundaryEventEscalationSubscriptionDTO ignored -> "D";
      case EventSubProcessCatchAllErrorSubscriptionDTO ignored -> "E";
      case EventSubProcessErrorSubscriptionDTO ignored -> "F";
      case EventSubProcessCatchAllEscalationSubscriptionDTO ignored -> "G";
      case EventSubProcessEscalationSubscriptionDTO ignored -> "H";
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
      case "A" -> context.constructType(BoundaryEventCatchAllErrorSubscriptionDTO.class);
      case "B" -> context.constructType(BoundaryEventErrorSubscriptionDTO.class);
      case "C" -> context.constructType(BoundaryEventCatchAllEscalationSubscriptionDTO.class);
      case "D" -> context.constructType(BoundaryEventEscalationSubscriptionDTO.class);
      case "E" -> context.constructType(EventSubProcessCatchAllErrorSubscriptionDTO.class);
      case "F" -> context.constructType(EventSubProcessErrorSubscriptionDTO.class);
      case "G" -> context.constructType(EventSubProcessCatchAllEscalationSubscriptionDTO.class);
      case "H" -> context.constructType(EventSubProcessEscalationSubscriptionDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
