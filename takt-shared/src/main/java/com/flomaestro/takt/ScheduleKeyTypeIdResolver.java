package com.flomaestro.takt;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.flomaestro.takt.dto.v_1_0_0.InstanceScheduleKeyDTO;
import java.io.IOException;

public class ScheduleKeyTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case InstanceScheduleKeyDTO instanceScheduleKeyDTO -> "I";
        //      case DefinitionScheduleKeyDTO definitionScheduleKeyDTO -> "D";
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
      case "I" -> SimpleType.construct(InstanceScheduleKeyDTO.class);
        //      case "D" -> SimpleType.construct(DefinitionScheduleKeyDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
