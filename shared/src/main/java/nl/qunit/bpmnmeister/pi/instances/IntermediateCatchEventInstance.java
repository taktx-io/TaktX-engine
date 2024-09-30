package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent2;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventStateEnum;

@NoArgsConstructor
@Getter
@Setter
public class IntermediateCatchEventInstance extends CatchEventInstance<IntermediateCatchEvent2> {
  private IntermediateCatchEventStateEnum state;

  public IntermediateCatchEventInstance(
      FLowNodeInstance parentInstance, IntermediateCatchEvent2 flowNode) {
    super(parentInstance, flowNode);
    state = IntermediateCatchEventStateEnum.READY;
  }

  @Override
  public boolean stateAllowsStart() {
    return state == IntermediateCatchEventStateEnum.READY;
  }

  @Override
  public boolean stateAllowsContinue() {
    return state == IntermediateCatchEventStateEnum.WAITING;
  }

  @Override
  public boolean isNotAwaiting() {
    return state != IntermediateCatchEventStateEnum.WAITING;
  }

  @Override
  public boolean isCompleted() {
    return state == IntermediateCatchEventStateEnum.FINISHED || state == IntermediateCatchEventStateEnum.TERMINATED;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return state == IntermediateCatchEventStateEnum.WAITING || state == IntermediateCatchEventStateEnum.READY;
  }

  @Override
  public void terminate() {
    // Do nothing
  }
}
