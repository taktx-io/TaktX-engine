package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EndEventInstanceDTO.class),
  @JsonSubTypes.Type(value = ExclusiveGatewayInstanceDTO.class),
  @JsonSubTypes.Type(value = ParallelGatewayInstanceDTO.class),
  @JsonSubTypes.Type(value = StartEventInstanceDTO.class),
  @JsonSubTypes.Type(value = IntermediateCatchEventInstanceDTO.class),
  @JsonSubTypes.Type(value = TaskInstanceDTO.class),
  @JsonSubTypes.Type(value = CallActivityInstanceDTO.class),
  @JsonSubTypes.Type(value = ServiceTaskInstanceDTO.class),
  @JsonSubTypes.Type(value = SendTaskInstanceDTO.class),
  @JsonSubTypes.Type(value = SubProcessInstanceDTO.class),
  @JsonSubTypes.Type(value = InclusiveGatewayInstanceDTO.class),
  @JsonSubTypes.Type(value = BoundaryEventInstanceDTO.class),
  @JsonSubTypes.Type(value = ReceiveTaskInstanceDTO.class),
  @JsonSubTypes.Type(value = MultiInstanceInstanceDTO.class),
  @JsonSubTypes.Type(value = ThrowEventInstanceDTO.class),
  @JsonSubTypes.Type(value = IntermediateThrowEventInstanceDTO.class),
})
@ToString
@SuperBuilder(toBuilder = true)
public abstract class FlowNodeInstanceDTO {
  private final UUID elementInstanceId;
  @Setter private UUID parentElementInstanceId;
  @Setter private String elementId;

  private final int passedCnt;

  protected FlowNodeInstanceDTO(UUID elementInstanceId, String elementId, int passedCnt) {
    this.elementInstanceId = elementInstanceId;
    this.elementId = elementId;
    this.passedCnt = passedCnt;
  }

  public boolean isTerminated() {
    return false;
  }

  public boolean isFailed() {
    return false;
  }
}
