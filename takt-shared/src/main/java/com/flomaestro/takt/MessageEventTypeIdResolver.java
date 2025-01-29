package com.flomaestro.takt;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.flomaestro.takt.dto.v_1_0_0.CancelCorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.CancelDefinitionMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageEventTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionMessageEventTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionMessageSubscriptionDTO;
import java.io.IOException;

public class MessageEventTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case DefinitionMessageSubscriptionDTO definitionMessageSubscriptionDTO -> "D";
      case CancelDefinitionMessageSubscriptionDTO cancelDefinitionMessageSubscriptionDTO -> "C";
      case CorrelationMessageSubscriptionDTO correlationMessageSubscriptionDTO -> "O";
      case CancelCorrelationMessageSubscriptionDTO cancelCorrelationMessageSubscriptionDTO -> "A";
      case DefinitionMessageEventTriggerDTO definitionMessageEventTriggerDTO -> "E";
      case CorrelationMessageEventTriggerDTO correlationMessageEventTriggerDTO -> "R";
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
      case "D" -> SimpleType.construct(DefinitionMessageSubscriptionDTO.class);
      case "C" -> SimpleType.construct(CancelDefinitionMessageSubscriptionDTO.class);
      case "O" -> SimpleType.construct(CorrelationMessageSubscriptionDTO.class);
      case "A" -> SimpleType.construct(CancelCorrelationMessageSubscriptionDTO.class);
      case "E" -> SimpleType.construct(DefinitionMessageEventTriggerDTO.class);
      case "R" -> SimpleType.construct(CorrelationMessageEventTriggerDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
