package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.StartEvent2;
import nl.qunit.bpmnmeister.pi.instances.StartEventInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class StartEventInstanceProcessor
    extends CatchEventInstanceProcessor<StartEvent2, StartEventInstance> {

  @Override
  protected InstanceResult processSpecificCatchEventInstance(
      FlowElements2 flowElements, StartEvent2 flowNode, StartEventInstance flowNodeInstance) {
    flowNodeInstance.setState(FlowNodeStateEnum.FINISHED);
    return new InstanceResult();
  }
}
