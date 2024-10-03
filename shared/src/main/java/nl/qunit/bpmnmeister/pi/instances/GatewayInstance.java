package nl.qunit.bpmnmeister.pi.instances;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.Gateway2;

@Setter
@Getter
@NoArgsConstructor
public abstract class GatewayInstance<N extends Gateway2> extends FLowNodeInstance<N> {
  private Set<String> selectedOutputFlows = new HashSet<>();

  protected GatewayInstance(FLowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }

  @Override
  public boolean stateAllowsStart() {
    return true;
  }

  @Override
  public boolean stateAllowsContinue() {
    return false;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return true;
  }

  @Override
  public boolean isNotAwaiting() {
    return true;
  }

  @Override
  public boolean isCompleted() {
    return true;
  }

  @Override
  public void terminate() {
    // Do nothing
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
