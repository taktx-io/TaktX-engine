package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pd.model.SequenceFlow;
import com.flomaestro.engine.pd.model.WithIoMapping;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceInfo;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public abstract class FLowNodeInstanceProcessor<
    E extends FlowNode, I extends FlowNodeInstance<?>, C extends ContinueFlowElementTriggerDTO> {
  protected IoMappingProcessor ioMappingProcessor;
  protected VariablesMapper variablesMapper;
  protected ProcessInstanceMapper processInstanceMapper;

  protected FLowNodeInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    this.ioMappingProcessor = ioMappingProcessor;
    this.processInstanceMapper = processInstanceMapper;
    this.variablesMapper = variablesMapper;
  }

  public void processStart(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      FlowNodeInstance<?> flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {

    if (!flownodeInstance.stateAllowsStart()) {
      return;
    }

    flownodeInstance.setDirty();

    E flowNode = (E) flownodeInstance.getFlowNode();
    Variables inputVariables = getInputVariables(flowNode, processInstanceVariables);

    this.processStartSpecificFlowNodeInstance(
        instanceResult,
        directInstanceResult,
        flowElements,
        (I) flownodeInstance,
        processInstance,
        inputFlowId,
        inputVariables);

    selectNextNodeIfAllowedStart(
        processInstance,
        (I) flownodeInstance,
        directInstanceResult,
        processInstanceVariables,
        isIterationInMultiInstance,
        flowNodeInstances);

    instanceResult.addProcessInstanceUpdate(
        createFlowNodeInstanceUpdate(
            processInstance,
            flowNodeInstances.getFlowNodeInstancesId(),
            flownodeInstance,
            processInstanceVariables));
  }

  public final void processContinue(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      FlowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger,
      Variables processInstanceVariables,
      Boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    if (!flowNodeInstance.stateAllowsContinue()) {
      return;
    }

    flowNodeInstance.setDirty();

    processInstanceVariables.merge(variablesMapper.fromDTO(trigger.getVariables()));

    this.processContinueSpecificFlowNodeInstance(
        instanceResult,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        processInstance,
        (I) flowNodeInstance,
        (C) trigger,
        processInstanceVariables,
        flowNodeInstances);

    selectNextNodeIfAllowedContinue(
        (I) flowNodeInstance,
        processInstance,
        directInstanceResult,
        processInstanceVariables,
        isIterationInMultiInstance,
        flowNodeInstances);

    instanceResult.addProcessInstanceUpdate(
        createFlowNodeInstanceUpdate(
            processInstance,
            flowNodeInstances.getFlowNodeInstancesId(),
            flowNodeInstance,
            processInstanceVariables));
  }

  public void processTerminate(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstance<?> instance,
      ProcessInstance processInstance,
      Variables variables,
      FlowNodeInstances flowNodeInstances) {
    // Only terminate if the instance is ready or waiting
    if (instance.stateAllowsTerminate()) {
      instance.setDirty();
      processTerminateSpecificFlowNodeInstance(
          instanceResult, directInstanceResult, (I) instance, processInstance, variables);
      instance.terminate();
      instanceResult.addProcessInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance, flowNodeInstances.getFlowNodeInstancesId(), instance, variables));
    }
  }

  protected Variables getInputVariables(E flowNode, Variables processInstanceVariables) {
    Variables inputVariables = processInstanceVariables;
    if (flowNode instanceof WithIoMapping withIoMapping) {
      inputVariables =
          ioMappingProcessor.getInputVariables(withIoMapping, processInstanceVariables);
    }
    return inputVariables;
  }

  protected void selectNextNodeIfAllowedStart(
      ProcessInstance processInstance,
      I flownodeInstance,
      DirectInstanceResult directInstanceResult,
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeStart()) {

      processNode(
          processInstance,
          flownodeInstance,
          directInstanceResult,
          processInstanceVariables,
          isIterationInMultiInstance,
          flowNodeInstances);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance,
      ProcessInstance processInstance,
      DirectInstanceResult directInstanceResult,
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeContinue()) {

      processNode(
          processInstance,
          flownodeInstance,
          directInstanceResult,
          processInstanceVariables,
          isIterationInMultiInstance,
          flowNodeInstances);
    }
  }

  protected void processNode(
      ProcessInstance processInstance,
      I flownodeInstance,
      DirectInstanceResult directInstanceResult,
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    FlowNode flowNode = flownodeInstance.getFlowNode();
    if (flowNode instanceof WithIoMapping withIoMapping) {
      Variables mappedOutputVariables = getOutputVariables(processInstanceVariables, withIoMapping);
      processInstanceVariables.merge(mappedOutputVariables);
    }

    flownodeInstance.increasePassedCnt();
    if (!isIterationInMultiInstance) {
      getSelectedSequenceFlows(
              processInstance, flownodeInstance, flowNodeInstances, processInstanceVariables)
          .forEach(
              sequenceFlow -> {
                FlowNodeInstance<?> fLowNodeInstance =
                    sequenceFlow
                        .getTargetNode()
                        .createAndStoreNewInstance(
                            flownodeInstance.getParentInstance(), flowNodeInstances);
                directInstanceResult.addNewFlowNodeInstance(
                    processInstance,
                    new FlowNodeInstanceInfo(fLowNodeInstance, sequenceFlow.getId()));
              });
    }
  }

  protected abstract Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      Variables variables);

  protected Variables getOutputVariables(
      Variables processInstanceVariables, WithIoMapping withIoMapping) {
    return ioMappingProcessor.getOutputVariables(withIoMapping, processInstanceVariables);
  }

  protected abstract void processStartSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables variables);

  protected abstract void processContinueSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I flowNodeInstance,
      C trigger,
      Variables variables,
      FlowNodeInstances flowNodeInstances);

  protected abstract void processTerminateSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      Variables variables);

  protected InstanceUpdateDTO createFlowNodeInstanceUpdate(
      ProcessInstance processInstance,
      UUID flowNodeInstancesId,
      FlowNodeInstance<?> flowNodeInstance,
      Variables processInstanceVariables) {
    VariablesDTO processInstanceVariablesDTO = variablesMapper.toDTO(processInstanceVariables);
    FlowNodeInstanceDTO flowNodeInstanceDTO = processInstanceMapper.map(flowNodeInstance);
    return new FlowNodeInstanceUpdateDTO(
        processInstance.getProcessInstanceKey(),
        flowNodeInstancesId,
        flowNodeInstanceDTO,
        processInstanceVariablesDTO);
  }
}
