package nl.qunit.bpmnmeister.pi.state;

import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public abstract class ActivityState extends FlowNodeStateDTO {

  private final int loopCnt;

  protected ActivityState(
      FlowNodeStateEnum state,
      String elementId,
      UUID elementInstanceId,
      int passedCnt,
      int loopCnt,
      String inputFlowId) {
    super(elementInstanceId, elementId, passedCnt, state, inputFlowId);
    this.loopCnt = loopCnt;
  }
}
