package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Event;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.EventInstance;

@NoArgsConstructor
public abstract class EventInstanceProcessor<E extends Event, I extends EventInstance<?>>
    extends FLowNodeInstanceProcessor<E, I, ContinueFlowElementTrigger> {

  protected EventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
  }

  @Override
  protected InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements flowElements, I flowNodeInstance, String inputFlowId, Variables variables) {
    return processStartSpecificEventInstance(
        flowElements, flowNodeInstance, inputFlowId, variables);
  }

  @Override
  protected InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements flowElements,
      I flowNodeInstance,
      ContinueFlowElementTrigger trigger,
      Variables variables,
      FlowNodeInstances flowNodeInstances) {
    // Should not occur
    return InstanceResult.empty();
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      I flowNodeInstance, FlowNodeInstances flowNodeInstances, Variables variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  protected abstract InstanceResult processStartSpecificEventInstance(
      FlowElements flowElements, I flowNodeInstance, String inputFlowId, Variables variables);
}
