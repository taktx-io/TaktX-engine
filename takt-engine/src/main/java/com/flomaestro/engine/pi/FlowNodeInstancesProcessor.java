package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessor;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;

@ApplicationScoped
public class FlowNodeInstancesProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FlowInstanceRunner flowInstanceRunner;
  private final VariablesMapper variablesMapper;
  private final Clock clock;

  public FlowNodeInstancesProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FlowInstanceRunner flowInstanceRunner,
      VariablesMapper variablesMapper,
      Clock clock) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
    this.variablesMapper = variablesMapper;
    this.clock = clock;
  }

  public void processStart(
      InstanceResult instanceResult,
      String elementId,
      FlowNodeInstance<?> parentElementInstance,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FlowNode flowNode = flowElements.getStartNode(elementId);
    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(parentElementInstance, flowNodeInstances);

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNode);

    processor.processStart(
        instanceResult,
        directInstanceResult,
        flowElements,
        flowNodeInstance,
        processInstance,
        Constants.NONE,
        processInstanceVariables,
        false,
        flowNodeInstances,
        processingStatistics);

    continueNewInstances(
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        processInstance,
        processInstanceVariables,
        processingStatistics);

    flowNodeInstances.determineImplicitCompletedState();

    if (flowNodeInstances.getState() == ProcessInstanceState.COMPLETED) {
      continueParentInstance(instanceResult, processInstance, processInstanceVariables);
    }
  }

  public void processContinue(
      InstanceResult instanceResult,
      int subProcessLevel,
      ContinueFlowElementTriggerDTO trigger,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {

    FlowNodeInstance<?> flowNodeInstance =
        flowNodeInstances.getInstanceWithInstanceId(
            trigger.getElementInstanceIdPath().get(subProcessLevel));

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    processor.processContinue(
        instanceResult,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        processInstance,
        flowNodeInstance,
        trigger,
        processInstanceVariables,
        false,
        flowNodeInstances,
        processingStatistics);

    continueNewInstances(
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        processInstance,
        processInstanceVariables,
        processingStatistics);

    flowNodeInstances.determineImplicitCompletedState();

    if (flowNodeInstances.getState() == ProcessInstanceState.COMPLETED) {
      continueParentInstance(instanceResult, processInstance, processInstanceVariables);
    }
  }

  public void processTerminate(
      InstanceResult instanceResult,
      TerminateTriggerDTO trigger,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      Variables processInstanceVariables,
      FlowElements flowElements,
      ProcessingStatistics processingStatistics) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    if (trigger.getElementInstanceIdPath().isEmpty()) {
      // Terminate all elements in the process instance and the process instance itself
      flowNodeInstances
          .getInstances()
          .values()
          .forEach(
              instance -> {
                FlowNodeInstanceProcessor<?, ?, ?> processor =
                    flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
                processor.processTerminate(
                    instanceResult,
                    directInstanceResult,
                    instance,
                    processInstance,
                    processInstanceVariables,
                    flowNodeInstances,
                    processingStatistics);
              });
      flowNodeInstances.setState(ProcessInstanceState.TERMINATED);
    } else {
      // Terminate the specific element instance in the process instance
      FlowNodeInstance<?> instance =
          flowNodeInstances.getInstanceWithInstanceId(
              trigger.getElementInstanceIdPath().getFirst());
      if (instance != null) {

        FlowNodeInstanceProcessor<?, ?, ?> processor =
            flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
        processor.processTerminate(
            instanceResult,
            directInstanceResult,
            instance,
            processInstance,
            processInstanceVariables,
            flowNodeInstances,
            processingStatistics);
      }
    }
    //    continueNewInstances(
    //        instanceResult,
    //        directInstanceResult,
    //        flowNodeInstances,
    //        flowElements,
    //        processInstance,
    //        processInstanceVariables,
    //        processingStatistics);
    flowNodeInstances.determineImplicitCompletedState();

  }

  protected void continueNewInstances(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      ProcessingStatistics processingStatistics) {

    flowInstanceRunner.continueNewInstances(
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        processInstance,
        flowElements,
        processInstanceVariables,
        processingStatistics);
  }

  private void continueParentInstance(
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      Variables processInstanceVariables) {
    if (!processInstance.getParentProcessInstanceKey().equals(Constants.NONE_UUID)) {
      instanceResult.addContinuation(
          new ContinueFlowElementTriggerDTO(
              processInstance.getParentProcessInstanceKey(),
              processInstance.getParentElementInstancePath(),
              Constants.NONE,
              variablesMapper.toDTO(processInstanceVariables)));
    }
  }
}
