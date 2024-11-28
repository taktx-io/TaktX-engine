package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Event;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.EventInstance;

@NoArgsConstructor
public abstract class EventInstanceProcessor<E extends Event, I extends EventInstance<?>>
    extends FLowNodeInstanceProcessor<E, I, ContinueFlowElementTrigger> {

  protected EventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
  }

  @Override
  protected void processStartSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables) {
    processStartSpecificEventInstance(
        processInstance,
        instanceResult,
        directInstanceResult,
        flowElements,
        flowNodeInstance,
        inputFlowId,
        variables);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I flowNodeInstance,
      ContinueFlowElementTrigger trigger,
      Variables variables,
      FlowNodeInstances flowNodeInstances) {
    // Should not occur
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      Variables variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  protected abstract void processStartSpecificEventInstance(
      ProcessInstance processInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      String inputFlowId,
      Variables variables);
}
