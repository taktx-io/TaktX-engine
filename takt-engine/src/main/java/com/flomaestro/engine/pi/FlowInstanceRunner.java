package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.EventSignal;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.BoundaryEventInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceInfo;
import com.flomaestro.engine.pi.model.FlowNodeInstanceVariables;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.processor.BoundaryEventInstanceProcessor;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessor;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
@RequiredArgsConstructor
public class FlowInstanceRunner {

  private final FlowNodeInstanceProcessorProvider processInstanceProcessorProvider;
  private final BoundaryEventInstanceProcessor boundaryEventProcessor;

  public void continueNewInstances(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstances flowNodeInstances,
      ProcessInstance processInstance,
      FlowElements flowElements,
      FlowNodeInstanceVariables flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {

    while (directInstanceResult.hasDirectTriggers()) {
      processDirectTriggers(
          flowNodeInstanceStore,
          flowNodeInstances,
          processInstance,
          instanceResult,
          directInstanceResult,
          flowElements,
          flowNodeInstanceVariables,
          processingStatistics);
    }
  }

  private void processDirectTriggers(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances,
      ProcessInstance processInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      FlowNodeInstanceVariables variables,
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
          variables,
          processingStatistics);
    }

    while (!directInstanceResult.terminateInstancesIsEmpty()) {
      UUID terminateInstance = directInstanceResult.pollTerminateInstance();
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(flowNodeInstances, flowNodeInstanceStore, flowElements);
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
          variables,
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
          variables,
          flowNodeInstances,
          processingStatistics);
    }
  }

  private void processEventByFlowNodeInstance(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      ProcessInstance processInstance,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstanceVariables variables,
      ProcessingStatistics processingStatistics) {

    boolean eventHandled = false;
    if (fLowNodeInstance instanceof ActivityInstance<?> activityInstance) {
      // First check for specific codes
      for (BoundaryEventInstance boundaryEventInstance :
          activityInstance.getAttachedBoundaryEventInstances()) {
        eventHandled =
            boundaryEventProcessor.processEvent(
                boundaryEventInstance,
                event,
                instanceResult,
                directInstanceResult,
                variables,
                processInstance,
                flowNodeInstances,
                processingStatistics);
        if (eventHandled) {
          break;
        }
      }

      // Check for catch all events
      if (!eventHandled) {
        for (BoundaryEventInstance boundaryEventInstance :
            activityInstance.getAttachedBoundaryEventInstances()) {
          eventHandled =
              boundaryEventProcessor.processEventCatchAll(
                  boundaryEventInstance,
                  event,
                  instanceResult,
                  directInstanceResult,
                  variables,
                  processInstance,
                  flowNodeInstances,
                  processingStatistics);
          if (eventHandled) {
            break;
          }
        }
      }
    }

    // Still not handled, bubble up if so defined
    if (!eventHandled && event.bubblesUp() && fLowNodeInstance.getParentInstance() != null) {
      directInstanceResult.addBubbleUpEvent(event);
    } else if (!eventHandled && event.bubblesUp() && fLowNodeInstance.getParentInstance() == null) {
      // Still not handled and No more bubbling up possible
      directInstanceResult.addTerminateInstance(event.getSourceInstance().getElementInstanceId());
    }
  }
}
