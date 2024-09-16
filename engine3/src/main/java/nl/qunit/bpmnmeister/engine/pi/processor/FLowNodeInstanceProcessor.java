package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
@Setter
@NoArgsConstructor
public abstract class FLowNodeInstanceProcessor<
    E extends FlowNode2, I extends FLowNodeInstance, C extends ContinueFlowElementTrigger2> {
  protected IoMappingProcessor ioMappingProcessor;

  protected FLowNodeInstanceProcessor(IoMappingProcessor ioMappingProcessor) {
    this.ioMappingProcessor = ioMappingProcessor;
  }

  public InstanceResult processStart(
      FlowElements2 flowElements,
      FLowNodeInstance<?> flownodeInstance,
      Variables2 processInstanceVariables,
      boolean isIterationInMultiInstance) {
    if (flownodeInstance.getState() != FlowNodeStateEnum.READY) {
      return InstanceResult.empty();
    }

    E flowNode = (E) flownodeInstance.getFlowNode();
    Variables2 inputVariables = getInputVariables(flowNode, processInstanceVariables);

    InstanceResult instanceResult =
        this.processStartSpecificFlowNodeInstance(
            flowElements, (I) flownodeInstance, inputVariables);

    processNodeIfFinished(
        flowElements,
        flownodeInstance,
        instanceResult,
        processInstanceVariables,
        isIterationInMultiInstance);

    return instanceResult;
  }

  public final InstanceResult processContinue(
      int subProcessLevel,
      FlowElements2 flowElements,
      FLowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 processInstanceVariables,
      Boolean isIterationInMultiInstance) {
    if (flowNodeInstance.getState() != FlowNodeStateEnum.WAITING) {
      return InstanceResult.empty();
    }

    InstanceResult instanceResult =
        this.processContinueSpecificFlowNodeInstance(
            subProcessLevel,
            flowElements,
            (I) flowNodeInstance,
            (C) trigger,
            processInstanceVariables);

    processNodeIfFinished(
        flowElements,
        flowNodeInstance,
        instanceResult,
        processInstanceVariables,
        isIterationInMultiInstance);

    return instanceResult;
  }

  public InstanceResult processTerminate(FlowNode2 flowNode, FLowNodeInstance<?> instance) {
    // Only terminate if the instance is ready or waiting
    if (instance.isAwaiting()) {
      InstanceResult instanceResult =
          processTerminateSpecificFlowNodeInstance((E) flowNode, (I) instance);
      instance.setState(FlowNodeStateEnum.TERMINATED);
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

  protected void processNodeIfFinished(
      FlowElements2 flowElements,
      FLowNodeInstance<?> flownodeInstance,
      InstanceResult instanceResult,
      Variables2 processInstanceVariables,
      boolean isIterationInMultiInstance) {
    if (flownodeInstance.getState() == FlowNodeStateEnum.FINISHED) {

      FlowNode2 flowNode = flownodeInstance.getFlowNode();
      if (flowNode instanceof WithIoMapping withIoMapping) {
        Variables2 mappedOutputVariables =
            getOutputVariables(processInstanceVariables, withIoMapping);
        processInstanceVariables.merge(mappedOutputVariables);
      }

      flownodeInstance.increasePassedCnt();
      Set<String> outgoing = flowNode.getOutgoing();
      if (!isIterationInMultiInstance) {
        outgoing.stream()
            .map(flowElements::getSequenceFlow)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(sequenceFlow2 -> flowElements.getFlowNode(sequenceFlow2.getTarget()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(node -> node.newInstance(flownodeInstance.getParentInstance()))
            .forEach(instanceResult::addNewFlowNodeInstance);
      }
    }
  }

  protected Variables2 getOutputVariables(
      Variables2 processInstanceVariables, WithIoMapping withIoMapping) {
    return ioMappingProcessor.getOutputVariables(withIoMapping, processInstanceVariables);
  }

  protected abstract InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements, I flownodeInstance, Variables2 variables);

  protected abstract InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      //      E flowNode,
      I flowNodeInstance,
      C trigger,
      Variables2 variables);

  protected abstract InstanceResult processTerminateSpecificFlowNodeInstance(
      E flowNode, I instance);
}
