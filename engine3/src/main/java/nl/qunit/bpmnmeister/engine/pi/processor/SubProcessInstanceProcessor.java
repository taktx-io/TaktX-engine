package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.FlowInstanceRunner;
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
  private FlowInstanceRunner flowInstanceRunner;

  @Inject
  public SubProcessInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceProcessorProvider processInstanceProcessorProvider,
      FlowInstanceRunner flowInstanceRunner) {
    super(ioMappingProcessor);
    this.processInstanceProcessorProvider = processInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements,
      SubProcessInstance subProcessInstance,
      Variables2 processInstanceVariables) {

    FlowNodeStates2 flowNodeStates = new FlowNodeStates2();
    subProcessInstance.setFlowNodeStates(flowNodeStates);
    subProcessInstance.setState(FlowNodeStateEnum.WAITING);

    FlowElements2 subProcessElements = subProcessInstance.getFlowNode().getElements();
    FlowNode2 startNode = subProcessElements.getStartNode(Constants.NONE);
    FLowNodeInstance<?> flowNodeInstance = startNode.newInstance(subProcessInstance);
    flowNodeStates.putInstance(flowNodeInstance);

    FLowNodeInstanceProcessor<?,?,?> processor = processInstanceProcessorProvider.getProcessor(startNode);

    InstanceResult instanceResult =
        processor.processStart(
            subProcessElements, flowNodeInstance, processInstanceVariables, false);

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
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 processInstanceVariables) {
    subProcessLevel++;

    FlowElements2 subProcessElements = subProcessInstance.getFlowNode().getElements();

    FLowNodeInstance<?> flowNodeInstance =
        subProcessInstance
            .getFlowNodeStates()
            .get(trigger.getElementInstanceIdPath().get(subProcessLevel));
    FLowNodeInstanceProcessor<?,?,?> processor =
        processInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    InstanceResult instanceResult =
        processor.processContinue(
            subProcessLevel,
            subProcessElements,
            flowNodeInstance,
            trigger,
            processInstanceVariables,
            false);

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

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(
      SubProcess2 subProcess, SubProcessInstance instance) {
    // Terminate all childelements
    InstanceResult instanceResult = InstanceResult.empty();
    FlowNodeStates2 flowNodeStates = instance.getFlowNodeStates();
    FlowElements2 subProcessElements = subProcess.getElements();
    flowNodeStates.setState(ProcessInstanceState.TERMINATED);
    flowNodeStates
        .getFlowNodeInstances()
        .values()
        .forEach(
            flowNodeInstance ->
              subProcessElements
                  .getFlowNode(flowNodeInstance.getFlowNode().getId())
                  .ifPresent(
                      flowNode -> {
                        FLowNodeInstanceProcessor<?,?,?> processor =
                            processInstanceProcessorProvider.getProcessor(flowNode);
                        instanceResult.merge(
                            processor.processTerminate(flowNode, flowNodeInstance));
                      })
            );
    return instanceResult;
  }

  private InstanceResult continueNewInstances(
      InstanceResult instanceResult,
      FlowNodeStates2 flowNodeStates,
      FlowElements2 flowElements,
      Variables2 variables) {
    while (instanceResult.hasNewFlowNodeInstances()) {
      instanceResult =
          flowInstanceRunner.processInstanceResult(flowNodeStates, instanceResult, flowElements, variables);
    }

    flowNodeStates.determineImplicitCompletedState();
    return instanceResult;
  }


}
