package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.engine.pi.processor.FLowNodeInstanceProcessor;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FlowNodeInstancesProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FlowInstanceRunner flowInstanceRunner;
  private final VariablesMapper variablesMapper;

  public FlowNodeInstancesProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FlowInstanceRunner flowInstanceRunner,
      VariablesMapper variablesMapper) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
    this.variablesMapper = variablesMapper;
  }

  public void processStart(
      InstanceResult instanceResult,
      String elementId,
      FlowNodeInstance<?> parentElementInstance,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FlowNode flowNode = flowElements.getStartNode(elementId);
    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(parentElementInstance, flowNodeInstances);

    FLowNodeInstanceProcessor<?, ?, ?> processor =
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
        flowNodeInstances);

    continueNewInstances(
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        processInstance,
        processInstanceVariables);
  }

  public void processContinue(
      InstanceResult instanceResult,
      int subProcessLevel,
      ContinueFlowElementTriggerDTO trigger,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {
    FlowNodeInstance<?> flowNodeInstance =
        flowNodeInstances.getInstanceWithInstanceId(
            trigger.getElementInstanceIdPath().get(subProcessLevel));
    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FLowNodeInstanceProcessor<?, ?, ?> processor =
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
        flowNodeInstances);

    continueNewInstances(
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        processInstance,
        processInstanceVariables);
  }

  public void processTerminate(
      InstanceResult instanceResult,
      TerminateTriggerDTO trigger,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowElements flowElements) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    if (trigger.getElementInstanceIdPath().isEmpty()) {
      // Terminate all elements in the process instance and the process instance itself
      FlowNodeInstances flowNodeInstances = processInstance.getFlowNodeInstances();
      flowNodeInstances
          .getInstances()
          .values()
          .forEach(
              instance -> {
                FLowNodeInstanceProcessor<?, ?, ?> processor =
                    flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
                processor.processTerminate(
                    instanceResult,
                    directInstanceResult,
                    instance,
                    processInstance,
                    processInstanceVariables,
                    flowNodeInstances);
              });
      flowNodeInstances.setStateDirty(ProcessInstanceState.TERMINATED);
    } else {
      // Terminate the specific element instance in the process instance
      FlowNodeInstances flowNodeInstances = processInstance.getFlowNodeInstances();
      FlowNodeInstance<?> instance =
          flowNodeInstances.getInstanceWithInstanceId(
              trigger.getElementInstanceIdPath().getFirst());
      if (instance != null) {
        FLowNodeInstanceProcessor<?, ?, ?> processor =
            flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
        processor.processTerminate(
            instanceResult,
            directInstanceResult,
            instance,
            processInstance,
            processInstanceVariables,
            flowNodeInstances);
      }
    }
    continueNewInstances(
        instanceResult,
        directInstanceResult,
        processInstance.getFlowNodeInstances(),
        flowElements,
        processInstance,
        processInstanceVariables);
  }

  protected void continueNewInstances(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables) {

    flowInstanceRunner.continueNewInstances(
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        processInstance,
        flowElements,
        processInstanceVariables);

    if (flowNodeInstances.getState() == ProcessInstanceState.COMPLETED) {
      continueParentInstance(instanceResult, processInstance, processInstanceVariables);
    }
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
