package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.FlowInstanceRunner;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.SubProcessInstance;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class SubProcessInstanceProcessor
    extends ActivityInstanceProcessor<SubProcess, SubProcessInstance, ContinueFlowElementTrigger> {

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
      FlowElements flowElements,
      SubProcessInstance subProcessInstance,
      String inputFlowId,
      Variables processInstanceVariables) {

    FlowNodeInstances flowNodeInstances = new FlowNodeInstances();
    subProcessInstance.setFlowNodeInstances(flowNodeInstances);
    subProcessInstance.setState(ActtivityStateEnum.WAITING);

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();
    FlowNode startNode = subProcessElements.getStartNode(Constants.NONE);
    FLowNodeInstance<?> flowNodeInstance =
        startNode.createAndStoreNewInstance(
            subProcessInstance, subProcessInstance.getFlowNodeInstances());

    FLowNodeInstanceProcessor<?, ?, ?> processor =
        processInstanceProcessorProvider.getProcessor(startNode);

    InstanceResult instanceResult =
        processor.processStart(
            subProcessElements,
            flowNodeInstance,
            Constants.NONE,
            processInstanceVariables,
            false,
            flowNodeInstances);

    instanceResult =
        flowInstanceRunner.continueNewInstances(
            instanceResult, flowNodeInstances, subProcessElements, processInstanceVariables);

    if (flowNodeInstances.getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }

    return instanceResult;
  }

  @Override
  protected InstanceResult processContinueSpecificActivityInstance(
      int subProcessLevel,
      FlowElements flowElements,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTrigger trigger,
      Variables processInstanceVariables) {
    subProcessLevel++;

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();

    FLowNodeInstance<?> flowNodeInstance =
        subProcessInstance
            .getFlowNodeInstances()
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
            subProcessInstance.getFlowNodeInstances());

    instanceResult =
        flowInstanceRunner.continueNewInstances(
            instanceResult,
            subProcessInstance.getFlowNodeInstances(),
            subProcessElements,
            processInstanceVariables);

    if (subProcessInstance.getFlowNodeInstances().getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }

    return instanceResult;
  }

  @Override
  protected InstanceResult processTerminateSpecificActivityInstance(SubProcessInstance instance) {
    // Terminate all childelements
    InstanceResult instanceResult = InstanceResult.empty();
    FlowNodeInstances flowNodeInstances = instance.getFlowNodeInstances();
    flowNodeInstances.setState(ProcessInstanceState.TERMINATED);
    flowNodeInstances
        .getInstances()
        .values()
        .forEach(
            flowNodeInstance -> {
              FLowNodeInstanceProcessor<?, ?, ?> processor =
                  processInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());
              instanceResult.merge(processor.processTerminate(flowNodeInstance));
            });
    return instanceResult;
  }
}
