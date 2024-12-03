package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EndEventDTO.class),
  @JsonSubTypes.Type(value = EventDefinitionDTO.class),
  @JsonSubTypes.Type(value = ExclusiveGatewayDTO.class),
  @JsonSubTypes.Type(value = ParallelGatewayDTO.class),
  @JsonSubTypes.Type(value = Process.class),
  @JsonSubTypes.Type(value = SubProcessDTO.class),
  @JsonSubTypes.Type(value = SequenceFlowDTO.class),
  @JsonSubTypes.Type(value = StartEventDTO.class),
  @JsonSubTypes.Type(value = IntermediateCatchEventDTO.class),
  @JsonSubTypes.Type(value = CallActivityDTO.class),
  @JsonSubTypes.Type(value = ServiceTaskDTO.class),
  @JsonSubTypes.Type(value = SendTaskDTO.class),
  @JsonSubTypes.Type(value = TaskDTO.class),
  @JsonSubTypes.Type(value = TimerEventDefinitionDTO.class),
  @JsonSubTypes.Type(value = LinkEventDefinitionDTO.class),
  @JsonSubTypes.Type(value = InclusiveGatewayDTO.class),
  @JsonSubTypes.Type(value = IntermediateThrowEventDTO.class),
  @JsonSubTypes.Type(value = TerminateEventDefinitionDTO.class),
  @JsonSubTypes.Type(value = ReceiveTaskDTO.class),
  @JsonSubTypes.Type(value = MessageEventDefinitionDTO.class),
  @JsonSubTypes.Type(value = BoundaryEventDTO.class),
  @JsonSubTypes.Type(value = EscalationEventDefinitionDTO.class),
  @JsonSubTypes.Type(value = ErrorEventDefinitionDTO.class),
})
public abstract class BaseElementDTO {
  private final String id;
  private final String parentId;

  protected BaseElementDTO(@Nonnull String id, String parentId) {
    this.id = id;
    this.parentId = parentId;
  }
}
