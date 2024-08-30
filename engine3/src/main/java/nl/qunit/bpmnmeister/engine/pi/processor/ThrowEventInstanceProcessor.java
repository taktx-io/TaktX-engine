package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent2;
import nl.qunit.bpmnmeister.pi.instances.ThrowEventInstance;

public abstract class ThrowEventInstanceProcessor<
        E extends ThrowEvent2, I extends ThrowEventInstance>
    extends EventInstanceProcessor<E, I> {

  @Override
  protected InstanceResult processStartSpecificEventInstance(
      FlowElements2 flowElements, E flowNode2, I flowNodeInstance) {
    return processSpecificThrowEventInstance(flowElements, flowNode2, flowNodeInstance);
  }

  protected abstract InstanceResult processSpecificThrowEventInstance(
      FlowElements2 flowElements, E flowNode2, I flowNodeInstance);
}
