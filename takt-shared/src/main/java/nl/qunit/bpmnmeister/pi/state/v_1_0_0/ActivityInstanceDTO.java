package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
public abstract class ActivityInstanceDTO extends FlowNodeInstanceDTO {

  private final ActtivityStateEnum state;
  private final int loopCnt;
  private final Set<UUID> boundaryEventIds;

  protected ActivityInstanceDTO(
      ActtivityStateEnum state,
      String elementId,
      UUID elementInstanceId,
      int passedCnt,
      int loopCnt,
      Set<UUID> boundaryEventIds) {
    super(elementInstanceId, elementId, passedCnt);
    this.state = state;
    this.loopCnt = loopCnt;
    this.boundaryEventIds = boundaryEventIds;
  }

  @Override
  public boolean isTerminated() {
    return state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public boolean isFailed() {
    return state == ActtivityStateEnum.FAILED;
  }
}
