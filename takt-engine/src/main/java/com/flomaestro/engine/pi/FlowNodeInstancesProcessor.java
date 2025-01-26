package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.EventSignal;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceVariables;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.FlowNodeInstancesVariables;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessor;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.util.UUID;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
public class FlowNodeInstancesProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FlowInstanceRunner flowInstanceRunner;
  private final Clock clock;

  public FlowNodeInstancesProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FlowInstanceRunner flowInstanceRunner,
      Clock clock) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
    this.clock = clock;
  }

  public void processStart(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      String elementId,
      FlowNodeInstance<?> parentElementInstance,
      FlowElements flowElements,
      ProcessInstance processInstance,
      FlowNodeInstancesVariables variables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    FlowNode flowNode = flowElements.getStartNode(elementId);
    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(parentElementInstance, flowNodeInstances);

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNode);

    FlowNodeInstanceVariables flowNodeInstanceVariableScope =
        variables.selectFlowNodeInstanceScope(flowNodeInstance.getElementInstanceId());

    processor.processStart(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowElements,
        flowNodeInstance,
        processInstance,
        Constants.NONE,
        flowNodeInstanceVariableScope,
        flowNodeInstances,
        processingStatistics);

    continueNewInstances(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        processInstance,
        flowNodeInstanceVariableScope,
        processingStatistics);

    flowNodeInstances.determineImplicitCompletedState();
  }

  public void processContinue(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      int subProcessLevel,
      ContinueFlowElementTriggerDTO trigger,
      FlowElements flowElements,
      ProcessInstance processInstance,
      FlowNodeInstancesVariables variables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(flowNodeInstances, flowNodeInstanceStore, flowElements);
    FlowNodeInstance<?> flowNodeInstance =
        storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
            trigger.getElementInstanceIdPath().get(subProcessLevel));

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();
    FlowNodeInstanceVariables flowNodeInstanceVariables =
        variables.selectFlowNodeInstanceScope(flowNodeInstance.getElementInstanceId());
    flowNodeInstanceVariables.merge(trigger.getVariables());
    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    processor.processContinue(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        processInstance,
        flowNodeInstance,
        trigger,
        flowNodeInstanceVariables,
        flowNodeInstances,
        processingStatistics);

    continueNewInstances(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        flowElements,
        processInstance,
        flowNodeInstanceVariables,
        processingStatistics);

    EventSignal eventSignal = directInstanceResult.pollBubbleUpEvent();
    while(eventSignal != null) {
      instanceResult.addBubbleUpEvent(eventSignal);
      eventSignal = directInstanceResult.pollBubbleUpEvent();
    }

    flowNodeInstances.determineImplicitCompletedState();

  }

  public void processTerminate(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      TerminateTriggerDTO trigger,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      FlowNodeInstancesVariables flowNodeInstancesVariables,
      FlowElements flowElements,
      ProcessingStatistics processingStatistics) {

    DirectInstanceResult directInstanceResult = DirectInstanceResult.empty();

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(flowNodeInstances, flowNodeInstanceStore, flowElements);

    if (trigger.getElementInstanceIdPath().isEmpty()) {
      // Terminate all elements in the process instance and the process instance itself
      storedFlowNodeInstancesWrapper.getAllInstances()
          .values()
          .forEach(
              instance -> {
                FlowNodeInstanceProcessor<?, ?, ?> processor =
                    flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
                FlowNodeInstanceVariables flowNodeInstanceVariables =
                    flowNodeInstancesVariables.selectFlowNodeInstanceScope(
                        instance.getElementInstanceId());
                processor.processTerminate(
                    flowNodeInstanceStore,
                    instanceResult,
                    directInstanceResult,
                    instance,
                    processInstance,
                    flowNodeInstanceVariables,
                    flowNodeInstances,
                    processingStatistics);
              });
      flowNodeInstances.setState(ProcessInstanceState.TERMINATED);
    } else {
      // Terminate the specific element instance in the process instance
      FlowNodeInstance<?> instance =
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              trigger.getElementInstanceIdPath().getFirst());
      if (instance != null) {
        FlowNodeInstanceVariables flowNodeInstanceVariables =
            flowNodeInstancesVariables.selectFlowNodeInstanceScope(instance.getElementInstanceId());
        FlowNodeInstanceProcessor<?, ?, ?> processor =
            flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
        processor.processTerminate(
            flowNodeInstanceStore,
            instanceResult,
            directInstanceResult,
            instance,
            processInstance,
            flowNodeInstanceVariables,
            flowNodeInstances,
            processingStatistics);
      }
    }
    flowNodeInstances.determineImplicitCompletedState();
  }

  protected void continueNewInstances(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      ProcessInstance processInstance,
      FlowNodeInstanceVariables processInstanceVariables,
      ProcessingStatistics processingStatistics) {

    flowInstanceRunner.continueNewInstances(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowNodeInstances,
        processInstance,
        flowElements,
        processInstanceVariables,
        processingStatistics);
  }
}
