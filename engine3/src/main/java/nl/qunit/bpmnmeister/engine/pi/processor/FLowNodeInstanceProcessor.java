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
      FlowNode2 flowNode,
      FLowNodeInstance flownodeInstance,
      Variables2 processInstanceVariables) {
    if (flownodeInstance.getState() != FlowNodeStateEnum.READY) {
      throw new IllegalStateException("FlowNodeInstance is not in READY state");
    }

    if (flowNode instanceof WithIoMapping withIoMapping) {
      Variables2 mappedInputVariables =
          ioMappingProcessor.getInputVariables(withIoMapping, processInstanceVariables);
      processInstanceVariables.merge(mappedInputVariables);
    }

    InstanceResult instanceResult =
        this.processStartSpecificFlowNodeInstance(
            flowElements, (E) flowNode, (I) flownodeInstance, processInstanceVariables);

    processNodeIfFinished(
        flowElements, flowNode, flownodeInstance, instanceResult, processInstanceVariables);

    return instanceResult;
  }

  protected void processNodeIfFinished(
      FlowElements2 flowElements,
      FlowNode2 flowNode,
      FLowNodeInstance flownodeInstance,
      InstanceResult instanceResult,
      Variables2 processInstanceVariables) {
    if (flownodeInstance.getState() == FlowNodeStateEnum.FINISHED) {

      if (flowNode instanceof WithIoMapping withIoMapping) {
        Variables2 mappedOutputVariables =
            ioMappingProcessor.getOutputVariables(withIoMapping, processInstanceVariables);
        processInstanceVariables.merge(mappedOutputVariables);
      }

      flownodeInstance.increasePassedCnt();
      Set<String> outgoing = flowNode.getOutgoing();
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

  public final InstanceResult processContinue(
      int subProcessLevel,
      FlowElements2 flowElements,
      FlowNode2 flowNode,
      FLowNodeInstance flowNodeInstance,
      C trigger,
      Variables2 processInstanceVariables) {
    if (flowNodeInstance.getState() != FlowNodeStateEnum.WAITING) {
      throw new IllegalStateException("FlowNodeInstance is not in ACTIVE state");
    }

    InstanceResult instanceResult =
        this.processContinueSpecificFlowNodeInstance(
            subProcessLevel,
            flowElements,
            (E) flowNode,
            (I) flowNodeInstance,
            trigger,
            processInstanceVariables);

    processNodeIfFinished(
        flowElements, flowNode, flowNodeInstance, instanceResult, processInstanceVariables);

    return instanceResult;
  }

  protected abstract InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements,
      E flowNode,
      I flownodeInstance,
      Variables2 processInstanceVariables);

  protected abstract InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      E flowNode,
      I flowNodeInstance,
      C trigger,
      Variables2 variables);
}
