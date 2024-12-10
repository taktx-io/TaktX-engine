package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.Activity;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.ActivityInstance;
import nl.qunit.bpmnmeister.engine.pi.model.BoundaryEventInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstanceInfo;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstances;
import nl.qunit.bpmnmeister.engine.pi.model.ProcessInstance;
import nl.qunit.bpmnmeister.engine.pi.model.Variables;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.Constants;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.ActtivityStateEnum;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ContinueFlowElementTriggerDTO;

@NoArgsConstructor
public abstract class ActivityInstanceProcessor<
    E extends Activity, I extends ActivityInstance<E>, C extends ContinueFlowElementTriggerDTO>
    extends FLowNodeInstanceProcessor<E, I, C> {

  protected ActivityInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
  }

  @Override
  protected final void processStartSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables) {

    processStartSpecificActivityInstance(
        instanceResult,
        directInstanceResult,
        flowElements,
        flownodeInstance,
        processInstance,
        inputFlowId,
        variables);

    if (flownodeInstance.getState() == ActtivityStateEnum.WAITING) {
      E flowNode = flownodeInstance.getFlowNode();
      flowNode
          .getBoundaryEvents()
          .forEach(
              boundaryEvent -> {
                BoundaryEventInstance boundaryEventInstance =
                    new BoundaryEventInstance(flownodeInstance.getParentInstance(), boundaryEvent);
                boundaryEventInstance.setAttachedInstanceId(
                    flownodeInstance.getElementInstanceId());
                flownodeInstance.addBoundaryEvent(boundaryEventInstance);
                directInstanceResult.addNewFlowNodeInstance(
                    processInstance,
                    new FlowNodeInstanceInfo(boundaryEventInstance, Constants.NONE));
              });
    }
  }

  @Override
  protected final void processContinueSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I flowNodeInstance,
      C trigger,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {

    processContinueSpecificActivityInstance(
        instanceResult,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        processInstance,
        flowNodeInstance,
        trigger,
        processInstanceVariables);

    if (flowNodeInstance.getState() == ActtivityStateEnum.FINISHED) {
      flowNodeInstance
          .getAttachedBoundaryEventInstances()
          .forEach(bi -> directInstanceResult.addTerminateInstance(bi.getElementInstanceId()));
    }
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      Variables variables) {
    instance
        .getAttachedBoundaryEventInstances()
        .forEach(bi -> directInstanceResult.addTerminateInstance(bi.getElementInstanceId()));
    processTerminateSpecificActivityInstance(
        instanceResult, directInstanceResult, instance, processInstance, variables);
  }

  protected abstract void processStartSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables);

  protected abstract void processContinueSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I externalTaskInstance,
      C trigger,
      Variables processInstanceVariables);

  protected abstract void processTerminateSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      Variables variables);

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      Variables variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }
}
