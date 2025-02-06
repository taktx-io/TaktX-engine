package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.EventSignal;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.BoundaryEventInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceInfo;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.engine.pi.processor.BoundaryEventInstanceProcessor;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessor;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
@RequiredArgsConstructor
public class FlowInstanceRunner {

  private final FlowNodeInstanceProcessorProvider processInstanceProcessorProvider;
  private final BoundaryEventInstanceProcessor boundaryEventProcessor;

  public void continueNewInstances(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstances flowNodeInstances,
      ProcessInstance processInstance,
      FlowElements flowElements,
      VariableScope parentVariableScope,
      ProcessingStatistics processingStatistics) {

    while (directInstanceResult.hasDirectTriggers()) {
      processDirectTriggers(
          flowNodeInstanceStore,
          flowNodeInstances,
          processInstance,
          instanceResult,
          directInstanceResult,
          flowElements,
          parentVariableScope,
          processingStatistics);
    }
  }

  private void processDirectTriggers(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances,
      ProcessInstance processInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      VariableScope parentVariableScope,
      ProcessingStatistics processingStatistics) {

    while (!directInstanceResult.eventsEmpty()) {
      EventSignal event = directInstanceResult.pollEvent();
      processEventByFlowNodeInstance(
          flowNodeInstanceStore,
          flowNodeInstances,
          flowElements,
          processInstance,
          event,
          event.getCurrentInstance(),
          instanceResult,
          directInstanceResult,
          parentVariableScope,
          processingStatistics);
    }

    while (!directInstanceResult.terminateInstancesIsEmpty()) {
      long terminateInstance = directInstanceResult.pollTerminateInstance();
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstance.getProcessInstanceKey(),
              flowNodeInstances,
              flowNodeInstanceStore,
              flowElements);
      FlowNodeInstance<?> flowNodeInstance =
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(terminateInstance);

      FlowNode node = flowNodeInstance.getFlowNode();

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(node);
      processor.processTerminate(
          flowNodeInstanceStore,
          instanceResult,
          directInstanceResult,
          flowNodeInstance,
          processInstance,
          parentVariableScope,
          flowNodeInstances,
          processingStatistics);
    }

    while (!directInstanceResult.newFlowNodeInstancesIsEmpty()) {
      FlowNodeInstanceInfo instanceInfo = directInstanceResult.pollNewFlowNodeInstance();
      FlowNodeInstance<?> fLowNodeInstance = instanceInfo.flowNodeInstance();
      flowNodeInstances.putInstance(fLowNodeInstance);
      FlowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(fLowNodeInstance.getFlowNode());
      processor.processStart(
          flowNodeInstanceStore,
          instanceResult,
          directInstanceResult,
          flowElements,
          instanceInfo.flowNodeInstance(),
          processInstance,
          instanceInfo.inputSequenceFlowId(),
          parentVariableScope,
          flowNodeInstances,
          processingStatistics);
    }
  }

  private void processEventByFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      ProcessInstance processInstance,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      VariableScope parentVariableScope,
      ProcessingStatistics processingStatistics) {

    if (fLowNodeInstance instanceof ActivityInstance<?> activityInstance) {
      boolean eventHandled = false;
      StoredFlowNodeInstancesWrapper instancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstance.getProcessInstanceKey(),
              flowNodeInstances,
              flowNodeInstanceStore,
              flowElements);
      // First check for specific codes
      for (long boundaryEventId : activityInstance.getBoundaryEventIds()) {
        BoundaryEventInstance boundaryEventInstance =
            (BoundaryEventInstance) instancesWrapper.getInstanceWithInstanceId(boundaryEventId);

        eventHandled =
            boundaryEventProcessor.processEvent(
                boundaryEventInstance,
                event,
                instanceResult,
                directInstanceResult,
                parentVariableScope,
                processInstance,
                flowNodeInstances,
                processingStatistics);
        if (eventHandled) {
          if (boundaryEventInstance.getFlowNode().isCancelActivity()) {
            directInstanceResult.addTerminateInstance(
                boundaryEventInstance.getAttachedInstanceId());
          }
          break;
        }
      }

      if (!eventHandled) {
        // If not handled by specific codes, check for catch all
        for (long boundaryEventId : activityInstance.getBoundaryEventIds()) {
          BoundaryEventInstance boundaryEventInstance =
              (BoundaryEventInstance) instancesWrapper.getInstanceWithInstanceId(boundaryEventId);
          if (!eventHandled) {
            eventHandled =
                boundaryEventProcessor.processEventCatchAll(
                    boundaryEventInstance,
                    event,
                    instanceResult,
                    directInstanceResult,
                    parentVariableScope,
                    processInstance,
                    flowNodeInstances,
                    processingStatistics);
          }
          if (eventHandled) {
            if (boundaryEventInstance.getFlowNode().isCancelActivity()) {
              directInstanceResult.addTerminateInstance(
                  boundaryEventInstance.getAttachedInstanceId());
            }
            break;
          }
        }
      }

      // Still not handled, bubble up if so defined
      if (!eventHandled && fLowNodeInstance.getParentInstance() != null) {
        directInstanceResult.addBubbleUpEvent(event);
      } else if (!eventHandled) {
        // Still not handled and No more bubbling up possible
        directInstanceResult.addTerminateInstance(
            event.getCurrentInstance().getElementInstanceId());
      }
    }
  }
}
