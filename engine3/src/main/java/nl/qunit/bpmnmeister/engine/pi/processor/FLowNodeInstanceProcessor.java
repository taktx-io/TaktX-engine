package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow2;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@Setter
@NoArgsConstructor
public abstract class FLowNodeInstanceProcessor<
    E extends FlowNode2, I extends FLowNodeInstance, C extends ContinueFlowElementTrigger2> {
  protected IoMappingProcessor ioMappingProcessor;
  protected VariablesMapper variablesMapper;

  protected FLowNodeInstanceProcessor(
      IoMappingProcessor ioMappingProcessor, VariablesMapper variablesMapper) {
    this.ioMappingProcessor = ioMappingProcessor;
    this.variablesMapper = variablesMapper;
  }

  public InstanceResult processStart(
      FlowElements2 flowElements,
      FLowNodeInstance<?> flownodeInstance,
      String inputFlowId,
      Variables2 processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowNodeStates2 flowNodeStates) {

    if (!flownodeInstance.stateAllowsStart()) {
      return InstanceResult.empty();
    }

    E flowNode = (E) flownodeInstance.getFlowNode();
    Variables2 inputVariables = getInputVariables(flowNode, processInstanceVariables);

    InstanceResult instanceResult =
        this.processStartSpecificFlowNodeInstance(
            flowElements, (I) flownodeInstance, inputFlowId, inputVariables);

    selectNextNodeIfAllowedStart(
        (I) flownodeInstance,
        instanceResult,
        processInstanceVariables,
        isIterationInMultiInstance,
        flowElements,
        flowNodeStates);

    return instanceResult;
  }

  public final InstanceResult processContinue(
      int subProcessLevel,
      FlowElements2 flowElements,
      FLowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 processInstanceVariables,
      Boolean isIterationInMultiInstance,
      FlowNodeStates2 flowNodeStates) {
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
            flowNodeStates);

    selectNextNodeIfAllowedContinue(
        (I) flowNodeInstance,
        instanceResult,
        processInstanceVariables,
        isIterationInMultiInstance,
        flowElements,
        flowNodeStates);

    return instanceResult;
  }

  public InstanceResult processTerminate(FLowNodeInstance<?> instance) {
    // Only terminate if the instance is ready or waiting
    if (instance.stateAllowsTerminate()) {
      InstanceResult instanceResult = processTerminateSpecificFlowNodeInstance((I) instance);
      instance.terminate();
      return instanceResult;
    }
    return InstanceResult.empty();
  }

  protected Variables2 getInputVariables(E flowNode, Variables2 processInstanceVariables) {
    Variables2 inputVariables = processInstanceVariables;
    if (flowNode instanceof WithIoMapping withIoMapping) {
      inputVariables =
          ioMappingProcessor.getInputVariables(withIoMapping, processInstanceVariables);
    }
    return inputVariables;
  }

  protected void selectNextNodeIfAllowedStart(
      I flownodeInstance,
      InstanceResult instanceResult,
      Variables2 processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates) {
    if (flownodeInstance.canSelectNextNodeStart()) {

      processNode(
          flownodeInstance,
          instanceResult,
          processInstanceVariables,
          isIterationInMultiInstance,
          flowElements,
          flowNodeStates);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance,
      InstanceResult instanceResult,
      Variables2 processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates) {
    if (flownodeInstance.canSelectNextNodeContinue()) {

      processNode(
          flownodeInstance,
          instanceResult,
          processInstanceVariables,
          isIterationInMultiInstance,
          flowElements,
          flowNodeStates);
    }
  }

  protected void processNode(
      I flownodeInstance,
      InstanceResult instanceResult,
      Variables2 processInstanceVariables,
      boolean isIterationInMultiInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates) {
    FlowNode2 flowNode = flownodeInstance.getFlowNode();
    if (flowNode instanceof WithIoMapping withIoMapping) {
      Variables2 mappedOutputVariables =
          getOutputVariables(processInstanceVariables, withIoMapping);
      processInstanceVariables.merge(mappedOutputVariables);
    }

    flownodeInstance.increasePassedCnt();
    if (!isIterationInMultiInstance) {
      getSelectedSequenceFlows(
              flownodeInstance, flowElements, flowNodeStates, processInstanceVariables)
          .forEach(
              sequenceFlow -> {
                FLowNodeInstance<?> fLowNodeInstance =
                    sequenceFlow
                        .getTargetNode()
                        .createAndStoreNewInstance(
                            flownodeInstance.getParentInstance(), flowNodeStates);
                instanceResult.addNewFlowNodeInstance(
                    new FLowNodeInstanceInfo(fLowNodeInstance, sequenceFlow.getId()));
              });
    }
  }

  protected abstract Set<SequenceFlow2> getSelectedSequenceFlows(
      I flowNodeInstance,
      FlowElements2 flowElements,
      FlowNodeStates2 flowNodeStates,
      Variables2 variables);

  protected Variables2 getOutputVariables(
      Variables2 processInstanceVariables, WithIoMapping withIoMapping) {
    return ioMappingProcessor.getOutputVariables(withIoMapping, processInstanceVariables);
  }

  protected abstract InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements, I flownodeInstance, String inputFlowId, Variables2 variables);

  protected abstract InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      I flowNodeInstance,
      C trigger,
      Variables2 variables,
      FlowNodeStates2 flowNodeStates);

  protected abstract InstanceResult processTerminateSpecificFlowNodeInstance(I instance);
}
