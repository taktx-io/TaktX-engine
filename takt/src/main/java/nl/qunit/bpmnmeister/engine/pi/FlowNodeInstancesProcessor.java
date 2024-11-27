package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.processor.FLowNodeInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.DirectInstanceResult;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@ApplicationScoped
public class FlowNodeInstancesProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FlowInstanceRunner flowInstanceRunner;
  private final VariablesMapper variablesMapper;
  private final ProcessInstanceMapper processInstanceMapper;

  public FlowNodeInstancesProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FlowInstanceRunner flowInstanceRunner,
      VariablesMapper variablesMapper,
      ProcessInstanceMapper processInstanceMapper) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
    this.variablesMapper = variablesMapper;
    this.processInstanceMapper = processInstanceMapper;
  }

  public void processStart(
      InstanceResult instanceResult,
      String elementId,
      FLowNodeInstance<?> parentElementInstance,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FlowNode flowNode = flowElements.getStartNode(elementId);
    FLowNodeInstance<?> flowNodeInstance =
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
      ContinueFlowElementTrigger trigger,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {
    FLowNodeInstance<?> flowNodeInstance =
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
      TerminateTrigger trigger,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowElements flowElements) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    if (trigger.getElementIdPath().isEmpty() && trigger.getElementInstanceIdPath().isEmpty()) {
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
      FLowNodeInstance<?> instance =
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
          new ContinueFlowElementTrigger(
              processInstance.getParentProcessInstanceKey(),
              processInstance.getParentElementIdPath(),
              processInstance.getParentElementInstancePath(),
              Constants.NONE,
              variablesMapper.toDTO(processInstanceVariables)));
    }
  }
}
