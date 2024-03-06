package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pd.model.*;

@Getter
@SuperBuilder
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

  protected BpmnElementState(StateEnum state) {
    this.state = state;
  }
}
