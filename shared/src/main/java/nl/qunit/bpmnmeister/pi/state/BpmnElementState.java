package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;
import lombok.Getter;

@Getter
@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EndEventState.class),
  @JsonSubTypes.Type(value = ExclusiveGatewayState.class),
  @JsonSubTypes.Type(value = ParallelGatewayState.class),
  @JsonSubTypes.Type(value = StartEventState.class),
  @JsonSubTypes.Type(value = TaskState.class),
  @JsonSubTypes.Type(value = ServiceTaskState.class),
  @JsonSubTypes.Type(value = MultiInstanceState.class),
  @JsonSubTypes.Type(value = SubProcessState.class),
})
public abstract class BpmnElementState {
  private final UUID elementInstanceId;
  private final int passedCnt;

  protected BpmnElementState(UUID elementInstanceId, int passedCnt) {
    this.elementInstanceId = elementInstanceId;
    this.passedCnt = passedCnt;
  }
}
