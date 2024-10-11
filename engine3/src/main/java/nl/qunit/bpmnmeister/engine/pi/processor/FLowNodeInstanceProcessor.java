package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@Setter
@NoArgsConstructor
public abstract class FLowNodeInstanceProcessor<
    E extends FlowNode, I extends FLowNodeInstance<?>, C extends ContinueFlowElementTrigger> {
  protected IoMappingProcessor ioMappingProcessor;
  protected VariablesMapper variablesMapper;

  protected FLowNodeInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    this.ioMappingProcessor = ioMappingProcessor;
    this.variablesMapper = variablesMapper;
  }

  public InstanceResult processStart(
      FlowElements flowElements,
      FLowNodeInstance<?> flownodeInstance,
      String inputFlowId,
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {

    if (!flownodeInstance.stateAllowsStart()) {
      return InstanceResult.empty();
    }

    E flowNode = (E) flownodeInstance.getFlowNode();
    Variables inputVariables = getInputVariables(flowNode, processInstanceVariables);

    InstanceResult instanceResult =
        this.processStartSpecificFlowNodeInstance(
            flowElements, (I) flownodeInstance, inputFlowId, inputVariables);

    selectNextNodeIfAllowedStart(
        (I) flownodeInstance,
        instanceResult,
        processInstanceVariables,
        isIterationInMultiInstance,
        flowNodeInstances);

    return instanceResult;
  }

  public final InstanceResult processContinue(
      int subProcessLevel,
      FlowElements flowElements,
      FLowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTrigger trigger,
      Variables processInstanceVariables,
      Boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    if (!flowNodeInstance.stateAllowsContinue()) {
      return InstanceResult.empty();
    }

    processInstanceVariables.merge(variablesMapper.fromDTO(trigger.getVariables()));

    InstanceResult instanceResult =
        this.processContinueSpecificFlowNodeInstance(
            subProcessLevel,
            flowElements,
            (I) flowNodeInstance,
            (C) trigger,
            processInstanceVariables,
            flowNodeInstances);

    selectNextNodeIfAllowedContinue(
        (I) flowNodeInstance,
        instanceResult,
        processInstanceVariables,
        isIterationInMultiInstance,
        flowNodeInstances);

    return instanceResult;
  }

  public InstanceResult processTerminate(FLowNodeInstance<?> instance, Variables variables) {
    // Only terminate if the instance is ready or waiting
    if (instance.stateAllowsTerminate()) {
      InstanceResult instanceResult =
          processTerminateSpecificFlowNodeInstance((I) instance, variables);
      instance.terminate();
      return instanceResult;
    }
    return InstanceResult.empty();
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
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeStart()) {

      processNode(
          flownodeInstance,
          instanceResult,
          processInstanceVariables,
          isIterationInMultiInstance,
          flowNodeInstances);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance,
      InstanceResult instanceResult,
      Variables processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeContinue()) {

      processNode(
          flownodeInstance,
          instanceResult,
          processInstanceVariables,
          isIterationInMultiInstance,
          flowNodeInstances);
    }
  }

  protected void processNode(
      I flownodeInstance,
      InstanceResult instanceResult,
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
                instanceResult.addNewFlowNodeInstance(
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

  protected abstract InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements flowElements, I flownodeInstance, String inputFlowId, Variables variables);

  protected abstract InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements flowElements,
      I flowNodeInstance,
      C trigger,
      Variables variables,
      FlowNodeInstances flowNodeInstances);

  protected abstract InstanceResult processTerminateSpecificFlowNodeInstance(
      I instance, Variables variables);
}
