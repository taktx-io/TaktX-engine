package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;

@Getter
@NoArgsConstructor
public abstract class FLowNodeInstance<N extends FlowNode2> {

  @Setter private UUID elementInstanceId;

  @Setter private int passedCnt;

  @Setter private String inputFlowId;

  @Setter private N flowNode;

  @Setter private FLowNodeInstance<?> parentInstance;

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
}
