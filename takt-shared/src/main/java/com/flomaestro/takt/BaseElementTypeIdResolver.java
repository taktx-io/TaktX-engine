package com.flomaestro.takt;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.flomaestro.takt.dto.v_1_0_0.BoundaryEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.CallActivityDTO;
import com.flomaestro.takt.dto.v_1_0_0.EndEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.ErrorEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.EscalationEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExclusiveGatewayDTO;
import com.flomaestro.takt.dto.v_1_0_0.InclusiveGatewayDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateCatchEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.IntermediateThrowEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.LinkEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParallelGatewayDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDTO;
import com.flomaestro.takt.dto.v_1_0_0.ReceiveTaskDTO;
import com.flomaestro.takt.dto.v_1_0_0.SendTaskDTO;
import com.flomaestro.takt.dto.v_1_0_0.SequenceFlowDTO;
import com.flomaestro.takt.dto.v_1_0_0.ServiceTaskDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.SubProcessDTO;
import com.flomaestro.takt.dto.v_1_0_0.TaskDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateEventDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.TimerEventDefinitionDTO;
import java.io.IOException;

public class BaseElementTypeIdResolver extends TypeIdResolverBase {

  @Override
  public String idFromValue(Object value) {
    return switch (value) {
      case BoundaryEventDTO boundaryEventDTO -> "B";
      case StartEventDTO startEventDTO -> "S";
      case IntermediateCatchEventDTO intermediateCatchEventDTO -> "IC";
      case IntermediateThrowEventDTO intermediateThrowEventDTO -> "IT";
      case EndEventDTO endEventDTO -> "E";
      case InclusiveGatewayDTO inclusiveGatewayDTO -> "IG";
      case ParallelGatewayDTO parallelGatewayDTO -> "PG";
      case ExclusiveGatewayDTO exclusiveGatewayDTO -> "EG";
      case SubProcessDTO subProcessDTO -> "SP";
      case CallActivityDTO callActivityDTO -> "CA";
      case ReceiveTaskDTO receiveTaskDTO -> "RT";
      case SendTaskDTO sendTaskDTO -> "ST";
      case ServiceTaskDTO serviceTaskDTO -> "SV";
      case TaskDTO taskDTO -> "T";
      case SequenceFlowDTO sequenceFlowDTO -> "Q";
      case ProcessDTO processDTO -> "P";
      case LinkEventDefinitionDTO linkEventDefinitionDTO -> "LE";
      case TerminateEventDefinitionDTO terminateEventDefinitionDTO -> "TE";
      case EscalationEventDefinitionDTO escalationEventDefinitionDTO -> "ES";
      case TimerEventDefinitionDTO timerEventDefinitionDTO -> "TM";
      case ErrorEventDefinitionDTO errorEventDefinitionDTO -> "ER";
      case MessageEventDefinitionDTO messageEventDefinitionDTO -> "ME";
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
      case "B" -> SimpleType.construct(BoundaryEventDTO.class);
      case "S" -> SimpleType.construct(StartEventDTO.class);
      case "IC" -> SimpleType.construct(IntermediateCatchEventDTO.class);
      case "IT" -> SimpleType.construct(IntermediateThrowEventDTO.class);
      case "E" -> SimpleType.construct(EndEventDTO.class);
      case "IG" -> SimpleType.construct(InclusiveGatewayDTO.class);
      case "PG" -> SimpleType.construct(ParallelGatewayDTO.class);
      case "EG" -> SimpleType.construct(ExclusiveGatewayDTO.class);
      case "SP" -> SimpleType.construct(SubProcessDTO.class);
      case "CA" -> SimpleType.construct(CallActivityDTO.class);
      case "RT" -> SimpleType.construct(ReceiveTaskDTO.class);
      case "ST" -> SimpleType.construct(SendTaskDTO.class);
      case "SV" -> SimpleType.construct(ServiceTaskDTO.class);
      case "T" -> SimpleType.construct(TaskDTO.class);
      case "Q" -> SimpleType.construct(SequenceFlowDTO.class);
      case "P" -> SimpleType.construct(ProcessDTO.class);
      case "LE" -> SimpleType.construct(LinkEventDefinitionDTO.class);
      case "TE" -> SimpleType.construct(TerminateEventDefinitionDTO.class);
      case "ES" -> SimpleType.construct(EscalationEventDefinitionDTO.class);
      case "TM" -> SimpleType.construct(TimerEventDefinitionDTO.class);
      case "ER" -> SimpleType.construct(ErrorEventDefinitionDTO.class);
      case "ME" -> SimpleType.construct(MessageEventDefinitionDTO.class);
      default -> throw new IllegalStateException("Unknown type: " + id);
    };
  }
}
