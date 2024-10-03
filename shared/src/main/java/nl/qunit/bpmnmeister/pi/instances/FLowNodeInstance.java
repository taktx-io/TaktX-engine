package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;

@Getter
@Setter
@NoArgsConstructor
public abstract class FLowNodeInstance<N extends FlowNode2> implements IFlowNodeInstance {

  private UUID elementInstanceId;

  private int passedCnt;

  private N flowNode;

  private UUID parentElementInstanceId;

  private FLowNodeInstance<?> parentInstance;

  protected FLowNodeInstance(FLowNodeInstance<?> parentInstance, N flowNode) {
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
