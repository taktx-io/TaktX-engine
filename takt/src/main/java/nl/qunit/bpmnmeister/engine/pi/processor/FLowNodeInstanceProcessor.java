package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.DirectInstanceResult;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstanceUpdate;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.InstanceUpdate;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstanceDTO;

@Getter
@Setter
@NoArgsConstructor
public abstract class FLowNodeInstanceProcessor<
    E extends FlowNode, I extends FLowNodeInstance<?>, C extends ContinueFlowElementTrigger> {
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
      FLowNodeInstance<?> flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {

    if (!flownodeInstance.stateAllowsStart()) {
      return;
    }

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
        (I) flownodeInstance,
        instanceResult,
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
      FLowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTrigger trigger,
      Variables processInstanceVariables,
      Boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    if (!flowNodeInstance.stateAllowsContinue()) {
      return;
    }

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
        instanceResult,
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
      FLowNodeInstance<?> instance,
      ProcessInstance processInstance,
      Variables variables,
      FlowNodeInstances flowNodeInstances) {
    // Only terminate if the instance is ready or waiting
    if (instance.stateAllowsTerminate()) {
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
      I flownodeInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeStart()) {

      processNode(
          flownodeInstance,
          directInstanceResult,
          processInstanceVariables,
          isIterationInMultiInstance,
          flowNodeInstances);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeContinue()) {

      processNode(
          flownodeInstance,
          directInstanceResult,
          processInstanceVariables,
          isIterationInMultiInstance,
          flowNodeInstances);
    }
  }

  protected void processNode(
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
      getSelectedSequenceFlows(flownodeInstance, flowNodeInstances, processInstanceVariables)
          .forEach(
              sequenceFlow -> {
                FLowNodeInstance<?> fLowNodeInstance =
                    sequenceFlow
                        .getTargetNode()
                        .createAndStoreNewInstance(
                            flownodeInstance.getParentInstance(), flowNodeInstances);
                directInstanceResult.addNewFlowNodeInstance(
                    new FLowNodeInstanceInfo(fLowNodeInstance, sequenceFlow.getId()));
              });
    }
  }

  protected abstract Set<SequenceFlow> getSelectedSequenceFlows(
      I flowNodeInstance, FlowNodeInstances flowNodeInstances, Variables variables);

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

  protected InstanceUpdate createFlowNodeInstanceUpdate(
      ProcessInstance processInstance,
      UUID flowNodeInstancesId,
      FLowNodeInstance<?> flowNodeInstance,
      Variables processInstanceVariables) {
    VariablesDTO processInstanceVariablesDTO = variablesMapper.toDTO(processInstanceVariables);
    FlowNodeInstanceDTO flowNodeInstanceDTO = processInstanceMapper.map(flowNodeInstance);
    return new FlowNodeInstanceUpdate(
        processInstance.getProcessInstanceKey(),
        flowNodeInstancesId,
        flowNodeInstanceDTO,
        processInstanceVariablesDTO);
  }
}
