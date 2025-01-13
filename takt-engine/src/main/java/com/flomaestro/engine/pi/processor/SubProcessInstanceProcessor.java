package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.SubProcess;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.FlowInstanceRunner;
import com.flomaestro.engine.pi.FlowNodeInstancesProcessor;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.SubProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class SubProcessInstanceProcessor
    extends ActivityInstanceProcessor<
        SubProcess, SubProcessInstance, ContinueFlowElementTriggerDTO> {

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
      VariablesMapper variablesMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, variablesMapper, clock);
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
      Variables processInstanceVariables,
      ProcessingStatistics processingStatistics) {

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
        flowNodeInstances,
        processingStatistics);

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
      ContinueFlowElementTriggerDTO trigger,
      Variables processInstanceVariables,
      ProcessingStatistics processingStatistics) {
    subProcessLevel++;

    FlowElements subProcessElements = subProcessInstance.getFlowNode().getElements();

    flowNodeInstancesProcessor.processContinue(
        instanceResult,
        subProcessLevel,
        trigger,
        subProcessElements,
        processInstance,
        processInstanceVariables,
        subProcessInstance.getFlowNodeInstances(),
        processingStatistics);

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
      Variables processInstanceVariables,
      ProcessingStatistics processingStatistics) {

    // Terminate all childelements
    FlowNodeInstances flowNodeInstances = subProcessInstance.getFlowNodeInstances();
    flowNodeInstances.setState(ProcessInstanceState.TERMINATED);

    DirectInstanceResult directInstanceResult1 = DirectInstanceResult.empty();
    for (FlowNodeInstance<?> fLowNodeInstance : flowNodeInstances.getInstances().values()) {
      FlowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(fLowNodeInstance.getFlowNode());

      processor.processTerminate(
          instanceResult,
          directInstanceResult1,
          fLowNodeInstance,
          processInstance,
          processInstanceVariables,
          flowNodeInstances,
          processingStatistics);

      flowInstanceRunner.continueNewInstances(
          instanceResult,
          directInstanceResult1,
          flowNodeInstances,
          processInstance,
          subProcessInstance.getFlowNode().getElements(),
          processInstanceVariables,
          processingStatistics);
    }
  }
}
