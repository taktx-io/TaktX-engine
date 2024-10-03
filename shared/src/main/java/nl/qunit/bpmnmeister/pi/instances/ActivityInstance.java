package nl.qunit.bpmnmeister.pi.instances;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@NoArgsConstructor
@Setter
@Getter
public abstract class ActivityInstance<N extends FlowNode> extends FLowNodeInstance<N> {
  private int loopCnt;
  private ActtivityStateEnum state;
  private Set<UUID> boundaryEventIds;

  protected ActivityInstance(FLowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
    this.state = ActtivityStateEnum.READY;
    this.boundaryEventIds = new HashSet<>();
  }

  public void addBoundaryEventId(UUID boundaryEventId) {
    boundaryEventIds.add(boundaryEventId);
  }

  public void increaseLoopCnt() {
    loopCnt++;
  }

  @Override
  public boolean stateAllowsStart() {
    return state == ActtivityStateEnum.READY;
  }

  @Override
  public boolean stateAllowsContinue() {
    return state == ActtivityStateEnum.WAITING;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return state == ActtivityStateEnum.READY || state == ActtivityStateEnum.WAITING;
  }

  @Override
  public boolean isNotAwaiting() {
    return state == ActtivityStateEnum.FINISHED || state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public boolean isCompleted() {
    return state == ActtivityStateEnum.FINISHED || state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public void terminate() {
    state = ActtivityStateEnum.TERMINATED;
  }

  @Override
  public boolean canSelectNextNodeStart() {
    return isCompleted();
  }

  @Override
  public boolean canSelectNextNodeContinue() {
    return isCompleted();
  }
}
