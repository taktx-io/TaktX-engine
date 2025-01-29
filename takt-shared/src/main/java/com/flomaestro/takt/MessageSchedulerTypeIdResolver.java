package com.flomaestro.takt;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.flomaestro.takt.dto.v_1_0_0.FixedRateMessageSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.OneTimeSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.RecurringMessageSchedulerDTO;
import java.io.IOException;

public class MessageSchedulerTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case RecurringMessageSchedulerDTO recurringMessageSchedulerDTO -> "R";
      case FixedRateMessageSchedulerDTO parsedDefinitionsDTO -> "F";
      case OneTimeSchedulerDTO oneTimeSchedulerDTO -> "O";
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
      case "R" -> SimpleType.construct(RecurringMessageSchedulerDTO.class);
      case "F" -> SimpleType.construct(FixedRateMessageSchedulerDTO.class);
      case "O" -> SimpleType.construct(OneTimeSchedulerDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
