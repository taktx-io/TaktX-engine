package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
@Setter
@NoArgsConstructor
public abstract class FLowNodeInstance {

  private UUID elementInstanceId;
  private int passedCnt;
  private FlowNodeStateEnum state;
  private String inputFlowId;
  private String elementId;

  protected FLowNodeInstance(String elementId) {
    this.state = FlowNodeStateEnum.READY;
    this.elementInstanceId = UUID.randomUUID();
    this.elementId = elementId;
  }

  public boolean isAwaiting() {
    return state == FlowNodeStateEnum.READY || state == FlowNodeStateEnum.WAITING;
  }

  public boolean isNotAwaiting() {
    return state == FlowNodeStateEnum.FINISHED
        || state == FlowNodeStateEnum.TERMINATED;
  }

  public void increasePassedCnt() {
    this.passedCnt++;
  }
}
