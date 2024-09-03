package nl.qunit.bpmnmeister.engine.pi.processor;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Activity2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;

@NoArgsConstructor
public abstract class ActivityInstanceProcessor<
        E extends Activity2, I extends ActivityInstance, C extends ContinueFlowElementTrigger2>
    extends FLowNodeInstanceProcessor<E, I, C> {

  protected ActivityInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected final InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements, E flowNode, I flownodeInstance, Variables2 variables) {
    return processStartSpecificActivityInstance(
        flowElements, flowNode, flownodeInstance, variables);
  }

  @Override
  protected final InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      E flowNode,
      I flowNodeInstance,
      C trigger,
      Variables2 variables) {
    return processContinueSpecificActivityInstance(
        subProcessLevel, flowElements, flowNode, flowNodeInstance, trigger, variables);
  }

  protected abstract InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements, E flowNode, I flownodeInstance, Variables2 variables);

  protected abstract InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      E externalTask,
      I externalTaskInstance,
      C trigger,
      Variables2 variables);
}
