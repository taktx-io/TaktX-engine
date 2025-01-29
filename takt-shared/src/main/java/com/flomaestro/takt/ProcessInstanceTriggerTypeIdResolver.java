package com.flomaestro.takt;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerTimeoutDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import java.io.IOException;

public class ProcessInstanceTriggerTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case ExternalTaskTriggerTimeoutDTO externalTaskTriggerTimeoutDTO -> "X";
      case ExternalTaskTriggerDTO externalTaskTriggerDTO -> "E";
      case StartFlowElementTriggerDTO startFlowElementTriggerDTO -> "S";
      case TerminateTriggerDTO terminateTriggerDTO -> "T";
      case ExternalTaskResponseTriggerDTO externalTaskResponseTriggerDTO -> "R";
      case ContinueFlowElementTriggerDTO continueFlowElementTriggerDTO -> "C";
      case StartCommandDTO startCommandDTO -> "A";
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
      case "X" -> SimpleType.construct(ExternalTaskTriggerTimeoutDTO.class);
      case "E" -> SimpleType.construct(ExternalTaskTriggerDTO.class);
      case "S" -> SimpleType.construct(StartFlowElementTriggerDTO.class);
      case "T" -> SimpleType.construct(TerminateTriggerDTO.class);
      case "R" -> SimpleType.construct(ExternalTaskResponseTriggerDTO.class);
      case "C" -> SimpleType.construct(ContinueFlowElementTriggerDTO.class);
      case "A" -> SimpleType.construct(StartCommandDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
