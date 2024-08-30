package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Getter
public abstract class FLowNodeInstanceProcessor<
    E extends FlowNode2, I extends FLowNodeInstance, C extends ContinueFlowElementTrigger2> {

  public InstanceResult processStart(
      FlowElements2 flowElements, FlowNode2 flowNode, FLowNodeInstance flownodeInstance) {
    if (flownodeInstance.getState() != FlowNodeStateEnum.READY) {
      throw new IllegalStateException("FlowNodeInstance is not in READY state");
    }

    InstanceResult instanceResult =
        this.processStartSpecificFlowNodeInstance(flowElements, (E) flowNode, (I) flownodeInstance);

    processNodeIfFinished(flowElements, flowNode, flownodeInstance, instanceResult);


    return instanceResult;
  }

  protected void processNodeIfFinished(
      FlowElements2 flowElements,
      FlowNode2 flowNode,
      FLowNodeInstance flownodeInstance,
      InstanceResult instanceResult) {
    if (flownodeInstance.getState() == FlowNodeStateEnum.FINISHED) {
      flownodeInstance.increasePassedCnt();
      Set<String> outgoing = flowNode.getOutgoing();
      outgoing.stream()
          .map(flowElements::getSequenceFlow)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .map(sequenceFlow2 -> flowElements.getFlowNode(sequenceFlow2.getTarget()))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .map(FlowNode2::newInstance)
          .forEach(instanceResult::addNewFlowNodeInstance);
    }
  }

  public final InstanceResult processContinue(
      FlowElements2 flowElements,
      FlowNode2 flowNode,
      FLowNodeInstance flowNodeInstance,
      C trigger,
      Variables2 variables) {
    if (flowNodeInstance.getState() != FlowNodeStateEnum.WAITING) {
      throw new IllegalStateException("FlowNodeInstance is not in ACTIVE state");
    }

    InstanceResult instanceResult =
        this.processContinueSpecificFlowNodeInstance(
            flowElements, (E) flowNode, (I) flowNodeInstance, trigger, variables);

    processNodeIfFinished(flowElements, flowNode, flowNodeInstance, instanceResult);

    return instanceResult;
  }

  protected abstract InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements, E flowNode, I flownodeInstance);

  protected abstract InstanceResult processContinueSpecificFlowNodeInstance(
      FlowElements2 flowElements,
      E flowNode,
      I flowNodeInstance,
      C trigger,
      Variables2 variables);
}
