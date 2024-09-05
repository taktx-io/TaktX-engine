package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SubProcess2;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.SubProcessInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class SubProcessInstanceProcessor
    extends ActivityInstanceProcessor<
        SubProcess2, SubProcessInstance, ContinueFlowElementTrigger2> {

  private ProcessInstanceProcessorProvider processInstanceProcessorProvider;

  @Inject
  public SubProcessInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceProcessorProvider processInstanceProcessorProvider) {
    super(ioMappingProcessor);
    this.processInstanceProcessorProvider = processInstanceProcessorProvider;
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements,
      SubProcess2 subProcess,
      SubProcessInstance subProcessInstance,
      Variables2 processInstanceVariables) {

    FlowNodeStates2 flowNodeStates = new FlowNodeStates2();
    subProcessInstance.setFlowNodeStates(flowNodeStates);
    subProcessInstance.setState(FlowNodeStateEnum.WAITING);

    FlowElements2 subProcessElements = subProcess.getElements();
    FlowNode2 startNode = subProcessElements.getStartNode(Constants.NONE);
    FLowNodeInstance flowNodeInstance = startNode.newInstance(subProcessInstance);
    flowNodeStates.putInstance(flowNodeInstance);

    FLowNodeInstanceProcessor processor = processInstanceProcessorProvider.getProcessor(startNode);

    InstanceResult instanceResult =
        processor.processStart(
            subProcessElements, startNode, flowNodeInstance, processInstanceVariables);

    instanceResult =
        continueNewInstances(
            instanceResult, flowNodeStates, subProcessElements, processInstanceVariables);

    if (flowNodeStates.getState().isFinished()) {
      subProcessInstance.setState(FlowNodeStateEnum.FINISHED);
    }

    return instanceResult;
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      SubProcess2 subProcess,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 processInstanceVariables) {
    subProcessLevel++;

    FlowElements2 subProcessElements = subProcess.getElements();
    FlowNode2 flowElement2 =
        subProcessElements.getFlowNode(trigger.getElementIdPath().get(subProcessLevel)).get();
    FLowNodeInstance flowNodeInstance =
        subProcessInstance
            .getFlowNodeStates()
            .get(trigger.getElementInstanceIdPath().get(subProcessLevel));
    FLowNodeInstanceProcessor processor =
        processInstanceProcessorProvider.getProcessor(flowElement2);

    InstanceResult instanceResult =
        processor.processContinue(
            subProcessLevel,
            subProcessElements,
            flowElement2,
            flowNodeInstance,
            trigger,
            processInstanceVariables);

    instanceResult =
        continueNewInstances(
            instanceResult,
            subProcessInstance.getFlowNodeStates(),
            subProcessElements,
            processInstanceVariables);

    if (subProcessInstance.getFlowNodeStates().getState().isFinished()) {
      subProcessInstance.setState(FlowNodeStateEnum.FINISHED);
    }

    return instanceResult;
  }

  protected InstanceResult continueNewInstances(
      InstanceResult instanceResult,
      FlowNodeStates2 flowNodeStates,
      FlowElements2 flowElements,
      Variables2 variables) {
    while (instanceResult.hasNewFlowNodeInstances()) {
      instanceResult =
          processInstanceResult(flowNodeStates, instanceResult, flowElements, variables);
    }

    determineImplicitCompletedState(flowNodeStates);
    return instanceResult;
  }

  private void determineImplicitCompletedState(FlowNodeStates2 flowNodeStates) {
    if (flowNodeStates.allMatch(FLowNodeInstance::isNotAwaiting)) {
      flowNodeStates.setState(ProcessInstanceState.COMPLETED);
    }
  }

  private InstanceResult processInstanceResult(
      FlowNodeStates2 flowNodeStates2,
      InstanceResult instanceResult,
      FlowElements2 flowElements,
      Variables2 variables) {
    InstanceResult newInstanceResult = new InstanceResult();
    List<FLowNodeInstance> newFlowNodeInstances = instanceResult.getNewFlowNodeInstances();
    for (FLowNodeInstance instance : newFlowNodeInstances) {
      flowNodeStates2.putInstance(instance);
      FlowNode2 node = flowElements.getFlowNode(instance.getElementId()).get();
      FLowNodeInstanceProcessor processor = processInstanceProcessorProvider.getProcessor(node);
      InstanceResult subInstanceResult =
          processor.processStart(flowElements, node, instance, variables);
      newInstanceResult.merge(subInstanceResult);
    }
    return newInstanceResult;
  }
}
