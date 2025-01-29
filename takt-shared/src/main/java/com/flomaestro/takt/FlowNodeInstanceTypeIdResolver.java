package com.flomaestro.takt;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.flomaestro.takt.dto.v_1_0_0.BoundaryEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.CallActivityInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.EndEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExclusiveGatewayInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.InclusiveGatewayInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateCatchEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateThrowEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.MultiInstanceInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParallelGatewayInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ReceiveTaskInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.SendTaskInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ServiceTaskInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartEventInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.SubProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.TaskInstanceDTO;
import java.io.IOException;

public class FlowNodeInstanceTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case CallActivityInstanceDTO callActivityInstanceDTO -> "C";
      case ReceiveTaskInstanceDTO receiveTaskInstanceDTO -> "R";
      case SendTaskInstanceDTO sendTaskInstanceDTO -> "D";
      case ServiceTaskInstanceDTO serviceTaskInstanceDTO -> "V";
      case TaskInstanceDTO taskInstanceDTO -> "T";
      case SubProcessInstanceDTO subProcessInstanceDTO -> "S";
      case MultiInstanceInstanceDTO multiInstanceInstanceDTO -> "M";
      case EndEventInstanceDTO endEventInstanceDTO -> "E";
      case IntermediateThrowEventInstanceDTO intermediateThrowEventInstanceDTO -> "W";
      case IntermediateCatchEventInstanceDTO intermediateCatchEventInstanceDTO -> "I";
      case StartEventInstanceDTO startEventInstanceDTO -> "A";
      case BoundaryEventInstanceDTO boundaryEventInstanceDTO -> "B";
      case InclusiveGatewayInstanceDTO inclusiveGatewayInstanceDTO -> "N";
      case ParallelGatewayInstanceDTO parallelGatewayInstanceDTO -> "P";
      case ExclusiveGatewayInstanceDTO parallelGatewayInstanceDTO -> "X";
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
      case "C" -> SimpleType.construct(CallActivityInstanceDTO.class);
      case "R" -> SimpleType.construct(ReceiveTaskInstanceDTO.class);
      case "D" -> SimpleType.construct(SendTaskInstanceDTO.class);
      case "V" -> SimpleType.construct(ServiceTaskInstanceDTO.class);
      case "T" -> SimpleType.construct(TaskInstanceDTO.class);
      case "S" -> SimpleType.construct(SubProcessInstanceDTO.class);
      case "M" -> SimpleType.construct(MultiInstanceInstanceDTO.class);
      case "E" -> SimpleType.construct(EndEventInstanceDTO.class);
      case "W" -> SimpleType.construct(IntermediateThrowEventInstanceDTO.class);
      case "I" -> SimpleType.construct(IntermediateCatchEventInstanceDTO.class);
      case "A" -> SimpleType.construct(StartEventInstanceDTO.class);
      case "B" -> SimpleType.construct(BoundaryEventInstanceDTO.class);
      case "N" -> SimpleType.construct(InclusiveGatewayInstanceDTO.class);
      case "P" -> SimpleType.construct(ParallelGatewayInstanceDTO.class);
      case "X" -> SimpleType.construct(ExclusiveGatewayInstanceDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
