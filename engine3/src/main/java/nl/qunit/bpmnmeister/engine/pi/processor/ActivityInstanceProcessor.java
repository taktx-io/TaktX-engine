package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Activity2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow2;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;

@NoArgsConstructor
public abstract class ActivityInstanceProcessor<
        E extends Activity2, I extends ActivityInstance<?>, C extends ContinueFlowElementTrigger2>
    extends FLowNodeInstanceProcessor<E, I, C> {

  protected ActivityInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected final InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements, I flownodeInstance, String inputFlowId, Variables2 variables) {
    return processStartSpecificActivityInstance(
        flowElements, flownodeInstance, inputFlowId, variables);
  }

  @Override
  protected final InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      //      E flowNode,
      I flowNodeInstance,
      C trigger,
      Variables2 processInstanceVariables,
      FlowNodeStates2 flowNodeStates) {
    return processContinueSpecificActivityInstance(
        subProcessLevel,
        flowElements,
        //        flowNode,
        flowNodeInstance,
        trigger,
        processInstanceVariables);
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(I instance) {
    return processTerminateSpecificActivityInstance(instance);
  }

  protected abstract InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements, I flownodeInstance, String inputFlowId, Variables2 variables);

  protected abstract InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      //      E externalTask,
      I externalTaskInstance,
      C trigger,
      Variables2 processInstanceVariables);

  protected abstract InstanceResult processTerminateSpecificActivityInstance(I instance);

  @Override
  protected Set<SequenceFlow2> getSelectedSequenceFlows(
      I flowNodeInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates,
      Variables2 variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }
}
