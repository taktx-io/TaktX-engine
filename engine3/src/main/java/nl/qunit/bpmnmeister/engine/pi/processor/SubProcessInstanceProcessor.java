package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.FlowInstanceRunner;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
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
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

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
      FlowInstanceRunner flowInstanceRunner,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, variablesMapper);
    this.processInstanceProcessorProvider = processInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
  }

  @Override
  protected InstanceResult processStartSpecificActivityInstance(
      FlowElements2 flowElements,
      SubProcessInstance subProcessInstance,
      String inputFlowId,
      Variables2 processInstanceVariables) {

    FlowNodeStates2 flowNodeStates = new FlowNodeStates2();
    subProcessInstance.setFlowNodeStates(flowNodeStates);
    subProcessInstance.setState(ActtivityStateEnum.WAITING);

    FlowElements2 subProcessElements = subProcessInstance.getFlowNode().getElements();
    FlowNode2 startNode = subProcessElements.getStartNode(Constants.NONE);
    FLowNodeInstance<?> flowNodeInstance =
        startNode.createAndStoreNewInstance(
            subProcessInstance, subProcessInstance.getFlowNodeStates());

    FLowNodeInstanceProcessor<?, ?, ?> processor =
        processInstanceProcessorProvider.getProcessor(startNode);

    InstanceResult instanceResult =
        processor.processStart(
            subProcessElements,
            flowNodeInstance,
            Constants.NONE,
            processInstanceVariables,
            false,
            flowNodeStates);

    instanceResult =
        continueNewInstances(
            instanceResult, flowNodeStates, subProcessElements, processInstanceVariables);

    if (flowNodeStates.getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
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
            .getInstanceWithInstanceId(trigger.getElementInstanceIdPath().get(subProcessLevel));
    FLowNodeInstanceProcessor<?, ?, ?> processor =
        processInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    InstanceResult instanceResult =
        processor.processContinue(
            subProcessLevel,
            subProcessElements,
            flowNodeInstance,
            trigger,
            processInstanceVariables,
            false,
            subProcessInstance.getFlowNodeStates());

    instanceResult =
        continueNewInstances(
            instanceResult,
            subProcessInstance.getFlowNodeStates(),
            subProcessElements,
            processInstanceVariables);

    if (subProcessInstance.getFlowNodeStates().getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }

    return instanceResult;
  }

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(SubProcessInstance instance) {
    // Terminate all childelements
    InstanceResult instanceResult = InstanceResult.empty();
    FlowNodeStates2 flowNodeStates = instance.getFlowNodeStates();
    flowNodeStates.setState(ProcessInstanceState.TERMINATED);
    flowNodeStates
        .getFlowNodeInstances()
        .values()
        .forEach(
            flowNodeInstance -> {
              FLowNodeInstanceProcessor<?, ?, ?> processor =
                  processInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());
              instanceResult.merge(processor.processTerminate(flowNodeInstance));
            });
    return instanceResult;
  }

  private InstanceResult continueNewInstances(
      InstanceResult instanceResult,
      FlowNodeStates2 flowNodeStates,
      FlowElements2 flowElements,
      Variables2 variables) {
    while (instanceResult.hasDirectTriggers()) {
      instanceResult =
          flowInstanceRunner.processDirectTriggers(
              flowNodeStates, instanceResult, flowElements, variables, flowNodeStates);
    }

    flowNodeStates.determineImplicitCompletedState();
    return instanceResult;
  }
}
