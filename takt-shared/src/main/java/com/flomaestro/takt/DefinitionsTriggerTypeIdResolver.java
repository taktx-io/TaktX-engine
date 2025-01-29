package com.flomaestro.takt;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionActivationDTO;
import com.flomaestro.takt.dto.v_1_0_0.XmlDefinitionsDTO;
import java.io.IOException;

public class DefinitionsTriggerTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case XmlDefinitionsDTO xmlDefinitionsDTO -> "X";
      case ParsedDefinitionsDTO parsedDefinitionsDTO -> "P";
      case ProcessDefinitionActivationDTO processDefinitionActivationDTO -> "A";
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
      case "X" -> SimpleType.construct(XmlDefinitionsDTO.class);
      case "P" -> SimpleType.construct(ParsedDefinitionsDTO.class);
      case "A" -> SimpleType.construct(ProcessDefinitionActivationDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
