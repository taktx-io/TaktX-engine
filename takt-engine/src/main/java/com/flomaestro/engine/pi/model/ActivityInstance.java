package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public abstract class ActivityInstance<N extends FlowNode> extends FlowNodeInstance<N> {
  private int loopCnt;
  private ActtivityStateEnum state = ActtivityStateEnum.INITIAL;
  private boolean stateChanged = false;
  private boolean stateWasWaiting = false;
  private Set<UUID> boundaryEventIds = new HashSet<>();

  private Set<BoundaryEventInstance> attachedBoundaryEventInstances = new HashSet<>();

  protected ActivityInstance(FlowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }

  public void addBoundaryEvent(BoundaryEventInstance boundaryEventInstance) {
    getBoundaryEventIds().add(boundaryEventInstance.getElementInstanceId());
    getAttachedBoundaryEventInstances().add(boundaryEventInstance);
  }

  public Set<UUID> getBoundaryEventIds() {
    if (boundaryEventIds == null) {
      boundaryEventIds = new HashSet<>();
    }
    return boundaryEventIds;
  }

  public Set<BoundaryEventInstance> getAttachedBoundaryEventInstances() {
    if (attachedBoundaryEventInstances == null) {
      attachedBoundaryEventInstances = new HashSet<>();
    }
    return attachedBoundaryEventInstances;
  }

  public void increaseLoopCnt() {
    loopCnt++;
  }

  @Override
  public void setStartedState() {
    setState(ActtivityStateEnum.STARTED);
  }

  public void setState(ActtivityStateEnum state) {
    if (this.state != ActtivityStateEnum.INITIAL && this.state != state) {
      stateChanged = true;
    }
    if (state == ActtivityStateEnum.WAITING) {
      stateWasWaiting = true;
    }
    this.state = state;
  }

  @Override
  public boolean wasAwaiting() {
    return stateWasWaiting;
  }

  @Override
  public boolean stateChanged() {
    return stateChanged;
  }

  @Override
  public boolean stateAllowsStart() {
    return state == ActtivityStateEnum.INITIAL;
  }

  @Override
  public boolean stateAllowsContinue() {
    return state == ActtivityStateEnum.WAITING;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return state == ActtivityStateEnum.INITIAL || state == ActtivityStateEnum.WAITING;
  }

  @Override
  public boolean isNotAwaiting() {
    return state == ActtivityStateEnum.FINISHED || state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public boolean isAwaiting() {
    return state == ActtivityStateEnum.WAITING;
  }

  @Override
  public boolean isCompleted() {
    return state == ActtivityStateEnum.FINISHED || state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public void terminate() {
    setState(ActtivityStateEnum.TERMINATED);
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
