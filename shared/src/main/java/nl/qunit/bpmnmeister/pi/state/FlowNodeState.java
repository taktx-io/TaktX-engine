package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EndEventState.class),
  @JsonSubTypes.Type(value = ExclusiveGatewayState.class),
  @JsonSubTypes.Type(value = ParallelGatewayState.class),
  @JsonSubTypes.Type(value = StartEventState.class),
  @JsonSubTypes.Type(value = IntermediateCatchEventState.class),
  @JsonSubTypes.Type(value = TaskState.class),
  @JsonSubTypes.Type(value = CallActivityState.class),
  @JsonSubTypes.Type(value = ServiceTaskState.class),
  @JsonSubTypes.Type(value = SendTaskState.class),
  @JsonSubTypes.Type(value = SubProcessState.class),
  @JsonSubTypes.Type(value = InclusiveGatewayState.class),
  @JsonSubTypes.Type(value = BoundaryEventState.class),
  @JsonSubTypes.Type(value = ReceiveTaskState.class),
})
@ToString
@SuperBuilder(toBuilder = true)
public abstract class FlowNodeState {
  private final UUID elementInstanceId;
  private final String elementId;
  private final int passedCnt;
  private final FlowNodeStateEnum state;
  private final String inputFlowId;

  protected FlowNodeState(
      UUID elementInstanceId, String elementId, int passedCnt, FlowNodeStateEnum state, String inputFlowId) {
    this.elementInstanceId = elementInstanceId;
    this.elementId = elementId;
    this.passedCnt = passedCnt;
    this.state = state;
    this.inputFlowId = inputFlowId;

  }
}
