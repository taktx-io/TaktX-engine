package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public abstract class EventState extends FlowNodeState {
  @JsonCreator
  protected EventState(
      UUID elementInstanceId,
      String elementId,
      int passedCnt,
      FlowNodeStateEnum flowNodeStateEnum,
      String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, flowNodeStateEnum, inputFlowId);
  }
}
