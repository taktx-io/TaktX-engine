package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.pd.model.Activity2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;

public abstract class ActivityInstanceProcessor<
        E extends Activity2, I extends ActivityInstance, C extends ContinueFlowElementTrigger2>
    extends FLowNodeInstanceProcessor<E, I, C> {

  @Override
  protected final InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements, E flowNode, I flownodeInstance) {
    return processStartSpecificActivityInstance(flowElements, flowNode, flownodeInstance);
  }

  @Override
  protected final InstanceResult processContinueSpecificFlowNodeInstance(
      FlowElements2 flowElements,
      E flowNode,
      I flowNodeInstance,
      C trigger,
      Variables2 variables) {
    return processContinueSpecificActivityInstance(
        flowElements, flowNode, flowNodeInstance, trigger, variables);
  }

  protected abstract InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements, E flowNode, I flownodeInstance);

  protected abstract InstanceResult processContinueSpecificActivityInstance(
      FlowElements2 flowElements,
      E externalTask,
      I externalTaskInstance,
      C trigger,
      Variables2 variables);
}
