package com.flomaestro.takt;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.flomaestro.takt.dto.v_1_0_0.FixedRateMessageScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.OneTimeScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.RecurringMessageScheduleDTO;
import java.io.IOException;

public class MessageSchedulerTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case RecurringMessageScheduleDTO recurringMessageSchedulerDTO -> "R";
      case FixedRateMessageScheduleDTO parsedDefinitionsDTO -> "F";
      case OneTimeScheduleDTO oneTimeSchedulerDTO -> "O";
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
  public JavaType typeFromId(DatabindContext context, String id) throws IOException {
    return switch (id) {
      case "R" -> SimpleType.construct(RecurringMessageScheduleDTO.class);
      case "F" -> SimpleType.construct(FixedRateMessageScheduleDTO.class);
      case "O" -> SimpleType.construct(OneTimeScheduleDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
