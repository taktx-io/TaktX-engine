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
import io.taktx.dto.subscriptions.CatchAllErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.CatchAllEscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.ErrorSubscriptionDTO;
import io.taktx.dto.subscriptions.EscalationSubscriptionDTO;
import io.taktx.dto.subscriptions.MessageSubscriptionDTO;
import io.taktx.dto.subscriptions.SignalSubscriptionDTO;
import io.taktx.dto.subscriptions.TimerSubscriptionDTO;

public class SubscriptionTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case CatchAllErrorSubscriptionDTO ignored -> "A";
      case ErrorSubscriptionDTO ignored -> "B";
      case CatchAllEscalationSubscriptionDTO ignored -> "C";
      case EscalationSubscriptionDTO ignored -> "D";
      case MessageSubscriptionDTO ignored -> "E";
      case TimerSubscriptionDTO ignored -> "F";
      case SignalSubscriptionDTO ignored -> "S";
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
      case "A" -> context.constructType(CatchAllErrorSubscriptionDTO.class);
      case "B" -> context.constructType(ErrorSubscriptionDTO.class);
      case "C" -> context.constructType(CatchAllEscalationSubscriptionDTO.class);
      case "D" -> context.constructType(EscalationSubscriptionDTO.class);
      case "E" -> context.constructType(MessageSubscriptionDTO.class);
      case "F" -> context.constructType(TimerSubscriptionDTO.class);
      case "S" -> context.constructType(SignalSubscriptionDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
