package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Event2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow2;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.EventInstance;

@NoArgsConstructor
public abstract class EventInstanceProcessor<E extends Event2, I extends EventInstance<?>>
    extends FLowNodeInstanceProcessor<E, I, ContinueFlowElementTrigger2> {

  protected EventInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements, I flowNodeInstance, String inputFlowId, Variables2 variables) {
    return processStartSpecificEventInstance(
        flowElements, flowNodeInstance, inputFlowId, variables);
  }

  @Override
  protected final InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      I flowNodeInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 variables,
      FlowNodeStates2 flowNodeStates) {
    // Should not occur
    return InstanceResult.empty();
  }

  @Override
  protected Set<SequenceFlow2> getSelectedSequenceFlows(I flowNodeInstance, Variables2 variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  protected abstract InstanceResult processStartSpecificEventInstance(
      FlowElements2 flowElements, I flowNodeInstance, String inputFlowId, Variables2 variables);
}
