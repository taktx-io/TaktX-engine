package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.pd.model.EndEvent2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.instances.EndEventInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class EndEventInstanceProcessor
    extends ThrowEventInstanceProcessor<EndEvent2, EndEventInstance> {

  @Override
  protected InstanceResult processSpecificThrowEventInstance(
      FlowElements2 flowElements, EndEvent2 flowNode2, EndEventInstance flowNodeInstance) {
    flowNodeInstance.setState(FlowNodeStateEnum.FINISHED);
    return new InstanceResult();
  }
}
