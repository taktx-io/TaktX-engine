package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.FlowNode;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public abstract class FlowNodeInstance<N extends FlowNode> implements IFlowNodeInstance {

  private UUID elementInstanceId;

  private int passedCnt;

  private N flowNode;

  private UUID parentElementInstanceId;

  private FlowNodeInstance<?> parentInstance;

  protected FlowNodeInstance(FlowNodeInstance<?> parentInstance, N flowNode) {
    this.parentInstance = parentInstance;
    this.elementInstanceId = UUID.randomUUID();
    this.flowNode = flowNode;
  }

  public void increasePassedCnt() {
    this.passedCnt++;
  }

  public abstract boolean stateAllowsStart();

  public abstract boolean stateAllowsTerminate();

  public abstract boolean stateAllowsContinue();

  public abstract boolean isNotAwaiting();

  public abstract boolean isCompleted();

  public abstract void terminate();

  public abstract boolean canSelectNextNodeStart();

  public abstract boolean canSelectNextNodeContinue();
}
