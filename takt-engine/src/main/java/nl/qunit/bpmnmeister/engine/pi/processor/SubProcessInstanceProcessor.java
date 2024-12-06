package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.SubProcess;
import nl.qunit.bpmnmeister.engine.pi.DirectInstanceResult;
import nl.qunit.bpmnmeister.engine.pi.FlowInstanceRunner;
import nl.qunit.bpmnmeister.engine.pi.FlowNodeInstancesProcessor;
import nl.qunit.bpmnmeister.engine.pi.InstanceResult;
import nl.qunit.bpmnmeister.engine.pi.ProcessInstanceMapper;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstances;
import nl.qunit.bpmnmeister.engine.pi.model.ProcessInstance;
import nl.qunit.bpmnmeister.engine.pi.model.SubProcessInstance;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

@ApplicationScoped
@NoArgsConstructor
public class SubProcessInstanceProcessor
    extends ActivityInstanceProcessor<SubProcess, SubProcessInstance, ContinueFlowElementTrigger> {

  private FlowNodeInstanceProcessorProvider processInstanceProcessorProvider;
  private FlowNodeInstancesProcessor flowNodeInstancesProcessor;
  private FlowInstanceRunner flowInstanceRunner;

  @Inject
  public SubProcessInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FlowNodeInstanceProcessorProvider processInstanceProcessorProvider,
      FlowNodeInstancesProcessor flowNodeInstancesProcessor,
      FlowInstanceRunner flowInstanceRunner,
      ProcessInstanceMapper processInstanceMapper,
      VariablesMapper variablesMapper) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper);
    this.processInstanceProcessorProvider = processInstanceProcessorProvider;
    this.flowNodeInstancesProcessor = flowNodeInstancesProcessor;
    this.flowInstanceRunner = flowInstanceRunner;
  }

  @Override
  protected void processStartSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      SubProcessInstance subProcessInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      Variables processInstanceVariables) {

    FlowNodeInstances flowNodeInstances = new FlowNodeInstances();
    subProcessInstance.setFlowNodeInstances(flowNodeInstances);
    subProcessInstance.setState(ActtivityStateEnum.WAITING);

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();

    flowNodeInstancesProcessor.processStart(
        instanceResult,
        Constants.NONE,
        subProcessInstance,
        subProcessElements,
        processInstance,
        processInstanceVariables,
        flowNodeInstances);

    if (flowNodeInstances.getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      SubProcessInstance subProcessInstance,
      ContinueFlowElementTrigger trigger,
      Variables processInstanceVariables) {
    subProcessLevel++;

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();

    flowNodeInstancesProcessor.processContinue(
        instanceResult,
        subProcessLevel,
        trigger,
        subProcessElements,
        processInstance,
        processInstanceVariables,
        subProcessInstance.getFlowNodeInstances());

    if (subProcessInstance.getFlowNodeInstances().getState().isFinished()) {
      subProcessInstance.setState(ActtivityStateEnum.FINISHED);
    }
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      SubProcessInstance subProcessInstance,
      ProcessInstance processInstance,
      Variables processInstanceVariables) {

    // Terminate all childelements
    FlowNodeInstances flowNodeInstances = subProcessInstance.getFlowNodeInstances();
    flowNodeInstances.setStateDirty(ProcessInstanceState.TERMINATED);

    DirectInstanceResult directInstanceResult1 = DirectInstanceResult.empty();
    for (FLowNodeInstance<?> fLowNodeInstance : flowNodeInstances.getInstances().values()) {
      FLowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(fLowNodeInstance.getFlowNode());

      processor.processTerminate(
          instanceResult,
          directInstanceResult1,
          fLowNodeInstance,
          processInstance,
          processInstanceVariables,
          flowNodeInstances);

      flowInstanceRunner.continueNewInstances(
          instanceResult,
          directInstanceResult1,
          flowNodeInstances,
          processInstance,
          subProcessInstance.getFlowNode().getElements(),
          processInstanceVariables);
    }
  }
}
