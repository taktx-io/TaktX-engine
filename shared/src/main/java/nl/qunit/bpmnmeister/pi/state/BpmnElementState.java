package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;
import lombok.Getter;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EndEventState.class, name = "EndEventState"),
  @JsonSubTypes.Type(value = ExclusiveGatewayState.class, name = "ExclusiveGatewayState"),
  @JsonSubTypes.Type(value = ParallelGatewayState.class, name = "ParallelGatewayState"),
  @JsonSubTypes.Type(value = StartEventState.class, name = "StartEventState"),
  @JsonSubTypes.Type(value = TaskState.class, name = "TaskState"),
  @JsonSubTypes.Type(value = ServiceTaskState.class, name = "ServiceTaskState"),
})
public abstract class BpmnElementState {
  StateEnum state;
  UUID elementInstanceId;

  protected BpmnElementState(StateEnum state, UUID elementInstanceId) {
    this.state = state;
    this.elementInstanceId = elementInstanceId;
  }

  public BpmnElementState terminate() {
    return null;
  }

  @Override
  public String toString() {
    return "BpmnElementState{"
        + "state="
        + state
        + ", elementInstanceId="
        + elementInstanceId
        + '}';
  }
}
