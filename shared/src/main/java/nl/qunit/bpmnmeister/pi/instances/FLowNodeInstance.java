package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
@NoArgsConstructor
public abstract class FLowNodeInstance<N extends FlowNode2> {

  @Setter private UUID elementInstanceId;

  @Setter private int passedCnt;

  @Setter private FlowNodeStateEnum state;

  @Setter private String inputFlowId;

  @Setter private N flowNode;

  @Setter private FLowNodeInstance<?> parentInstance;

  protected FLowNodeInstance(FLowNodeInstance<?> parentInstance, N flowNode) {
    this.parentInstance = parentInstance;
    this.state = FlowNodeStateEnum.READY;
    this.elementInstanceId = UUID.randomUUID();
    this.flowNode = flowNode;
  }

  public boolean isAwaiting() {
    return state == FlowNodeStateEnum.READY || state == FlowNodeStateEnum.WAITING;
  }

  public boolean isCompleted() {
    return state == FlowNodeStateEnum.FINISHED || state == FlowNodeStateEnum.TERMINATED;
  }

  public boolean isNotAwaiting() {
    return state == FlowNodeStateEnum.FINISHED || state == FlowNodeStateEnum.TERMINATED;
  }

  public void increasePassedCnt() {
    this.passedCnt++;
  }
}
