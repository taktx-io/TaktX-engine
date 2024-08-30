package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.pd.model.CatchEvent2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.instances.CatchEventInstance;

public abstract class CatchEventInstanceProcessor<
        E extends CatchEvent2, I extends CatchEventInstance>
    extends EventInstanceProcessor<E, I> {

  @Override
  protected InstanceResult processStartSpecificEventInstance(
      FlowElements2 flowElements, E flowNode2, I flowNodeInstance) {
    return processSpecificCatchEventInstance(flowElements, flowNode2, flowNodeInstance);
  }

  protected abstract InstanceResult processSpecificCatchEventInstance(
      FlowElements2 flowElements, E flowNode2, I flowNodeInstance);
}
