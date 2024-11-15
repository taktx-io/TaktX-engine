package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.processor.FLowNodeInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

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

  public InstanceResult processStart(
      StartNewProcessInstanceTrigger startNewProcessInstanceTrigger,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariablee,
      FlowNodeInstances flowNodeInstances) {
    FlowNode flowNode = flowElements.getStartNode(startNewProcessInstanceTrigger.getElementId());
    FLowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(null, flowNodeInstances);

    FLowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNode);

    InstanceResult instanceResult =
        processor.processStart(
            flowElements,
            flowNodeInstance,
            Constants.NONE,
            processInstanceVariablee,
            false,
            flowNodeInstances);

    return continueNewInstances(
        instanceResult, flowNodeInstances, flowElements, processInstance, processInstanceVariablee);
  }

  public InstanceResult processContinue(
      ContinueFlowElementTrigger trigger,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {
    FLowNodeInstance<?> flowNodeInstance =
        flowNodeInstances.getInstanceWithInstanceId(trigger.getElementInstanceIdPath().getFirst());

    FLowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    InstanceResult instanceResult =
        processor.processContinue(
            0,
            flowElements,
            flowNodeInstance,
            trigger,
            processInstanceVariables,
            false,
            flowNodeInstances);

    return continueNewInstances(
        instanceResult, flowNodeInstances, flowElements, processInstance, processInstanceVariables);
  }

  public InstanceResult processTerminate(
      TerminateTrigger trigger,
      ProcessInstance processInstance,
      Variables processInstanceVariables,
      FlowElements flowElements) {
    InstanceResult instanceResult = InstanceResult.empty();
    if (trigger.getElementIdPath().isEmpty() && trigger.getElementInstanceIdPath().isEmpty()) {
      // Terminate all elements in the process instance and the process instance itself
      processInstance
          .getFlowNodeInstances()
          .getInstances()
          .values()
          .forEach(
              instance -> {
                FLowNodeInstanceProcessor<?, ?, ?> processor =
                    flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
                instanceResult.merge(
                    processor.processTerminate(instance, processInstanceVariables));
              });
      processInstance.getFlowNodeInstances().setState(ProcessInstanceState.TERMINATED);
    } else {
      // Terminate the specific element instance in the process instance
      FLowNodeInstance<?> instance =
          processInstance
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(trigger.getElementInstanceIdPath().getFirst());
      if (instance != null) {
        FLowNodeInstanceProcessor<?, ?, ?> processor =
            flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
        instanceResult.merge(processor.processTerminate(instance, processInstanceVariables));
      }
    }
    return continueNewInstances(
        instanceResult,
        processInstance.getFlowNodeInstances(),
        flowElements,
        processInstance,
        processInstanceVariables);
  }

  protected InstanceResult continueNewInstances(
      InstanceResult instanceResult,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      ProcessInstance processInstance,
      Variables processInstanceVariables) {

    instanceResult =
        flowInstanceRunner.continueNewInstances(
            instanceResult, flowNodeInstances, flowElements, processInstanceVariables);

    if (flowNodeInstances.getState() == ProcessInstanceState.COMPLETED) {
      processInstanceFinished(instanceResult, processInstance, processInstanceVariables);
    }
    return instanceResult;
  }

  private void processInstanceFinished(
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
