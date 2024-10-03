package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;

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
