package nl.qunit.bpmnmeister.engine.pi.processor;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Event2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.EventInstance;

@NoArgsConstructor
public abstract class EventInstanceProcessor<E extends Event2, I extends EventInstance>
    extends FLowNodeInstanceProcessor<E, I, ContinueFlowElementTrigger2> {

  protected EventInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    super(ioMappingProcessor);
  }

  @Override
  protected InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements, E flowNode2, I flowNodeInstance, Variables2 variables) {
    return processStartSpecificEventInstance(flowElements, flowNode2, flowNodeInstance, variables);
  }

  @Override
  protected final InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      E flowNode,
      I flowNodeInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 variables) {
    // Should not occur
    return InstanceResult.empty();
  }

  protected abstract InstanceResult processStartSpecificEventInstance(
      FlowElements2 flowElements, E flowNode2, I flowNodeInstance, Variables2 variables);
}
