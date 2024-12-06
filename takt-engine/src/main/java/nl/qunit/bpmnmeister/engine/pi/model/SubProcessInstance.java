package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pd.model.SubProcess;

@NoArgsConstructor
@Setter
@Getter
public class SubProcessInstance extends ActivityInstance<SubProcess>
    implements WithFlowNodeInstances {

  private FlowNodeInstances flowNodeInstances = new FlowNodeInstances();

  public SubProcessInstance(FLowNodeInstance<?> parentInstance, SubProcess flowNode) {
    super(parentInstance, flowNode);
  }
}
