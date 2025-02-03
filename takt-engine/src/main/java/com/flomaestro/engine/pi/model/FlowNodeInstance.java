package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.FlowNode;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public abstract class FlowNodeInstance<N extends FlowNode> implements IFlowNodeInstance {

  private long elementInstanceId;

  private int passedCnt;

  private N flowNode;

  private FlowNodeInstance<?> parentInstance;

  private boolean dirty = false;

  protected FlowNodeInstance(
      FlowNodeInstance<?> parentInstance, N flowNode, long elementInstanceId) {
    this.parentInstance = parentInstance;
    this.elementInstanceId = elementInstanceId;
    this.flowNode = flowNode;
  }

  @Override
  public List<Long> getKeyPath() {
    List<Long> parentKeyPath =
        parentInstance != null ? parentInstance.getKeyPath() : new ArrayList<>();
    parentKeyPath.add(elementInstanceId);
    return parentKeyPath;
  }

  public void increasePassedCnt() {
    this.passedCnt++;
    setDirty();
  }

  public boolean isDirty() {
    boolean result = dirty;
    if (!dirty && this instanceof WithFlowNodeInstances withFlowNodeInstances) {
      result |= withFlowNodeInstances.getFlowNodeInstances().isDirty();
    }
    return result;
  }

  public abstract boolean stateAllowsStart();

  public abstract boolean stateAllowsTerminate();

  public abstract boolean stateAllowsContinue();

  public abstract boolean isNotAwaiting();

  public abstract boolean isCompleted();

  public abstract void terminate();

  public abstract boolean canSelectNextNodeStart();

  public abstract boolean canSelectNextNodeContinue();

  public void setDirty() {
    dirty = true;
  }

  public abstract void setStartedState();

  public abstract boolean wasNew();

  public abstract boolean stateChanged();

  public abstract boolean isAwaiting();

  public abstract boolean wasAwaiting();

  public abstract void setInitialState();
}
