package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.Activity;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.SequenceFlow;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.BoundaryEventInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceInfo;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import java.time.Clock;
import java.util.Set;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class ActivityInstanceProcessor<
        E extends Activity, I extends ActivityInstance<E>, C extends ContinueFlowElementTriggerDTO>
    extends FlowNodeInstanceProcessor<E, I, C> {

  protected ActivityInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper, clock);
  }

  @Override
  protected final void processStartSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables,
      ProcessingStatistics processingStatistics) {

    processStartSpecificActivityInstance(
        instanceResult,
        directInstanceResult,
        flowElements,
        flownodeInstance,
        processInstance,
        inputFlowId,
        variables,
        processingStatistics);

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
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {

    processContinueSpecificActivityInstance(
        instanceResult,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        processInstance,
        flowNodeInstance,
        trigger,
        processInstanceVariables,
        processingStatistics);

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
      Variables variables,
      ProcessingStatistics processingStatistics) {
    instance
        .getAttachedBoundaryEventInstances()
        .forEach(bi -> directInstanceResult.addTerminateInstance(bi.getElementInstanceId()));
    processTerminateSpecificActivityInstance(
        instanceResult,
        directInstanceResult,
        instance,
        processInstance,
        variables,
        processingStatistics);
  }

  protected abstract void processStartSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables,
      ProcessingStatistics processingStatistics);

  protected abstract void processContinueSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I externalTaskInstance,
      C trigger,
      Variables processInstanceVariables,
      ProcessingStatistics processingStatistics);

  protected abstract void processTerminateSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      Variables variables,
      ProcessingStatistics processingStatistics);

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      Variables variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }
}
