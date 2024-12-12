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
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.engine.pi.processor.BoundaryEventInstanceProcessor;
import com.flomaestro.engine.pi.processor.FLowNodeInstanceProcessor;
import com.flomaestro.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class FlowInstanceRunner {

  private final FlowNodeInstanceProcessorProvider processInstanceProcessorProvider;
  private final BoundaryEventInstanceProcessor boundaryEventProcessor;

  public void continueNewInstances(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstances flowNodeInstances,
      ProcessInstance processInstance,
      FlowElements flowElements,
      Variables processInstanceVariables) {
    while (directInstanceResult.hasDirectTriggers()) {
      processDirectTriggers(
          flowNodeInstances,
          processInstance,
          instanceResult,
          directInstanceResult,
          flowElements,
          processInstanceVariables);
    }

    flowNodeInstances.determineImplicitCompletedState();
  }

  private void processDirectTriggers(
      FlowNodeInstances flowNodeInstances,
      ProcessInstance processInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      Variables variables) {

    while (!directInstanceResult.eventsEmpty()) {
      EventSignal event = directInstanceResult.pollEvent();
      processEventByFlowNodeInstance(
          flowNodeInstances,
          flowElements,
          processInstance,
          event,
          event.getSourceInstance(),
          instanceResult,
          directInstanceResult,
          variables);
    }

    while (!directInstanceResult.terminateInstancesIsEmpty()) {
      UUID terminateInstance = directInstanceResult.pollTerminateInstance();
      FlowNodeInstance<?> activityInstance =
          flowNodeInstances.getInstanceWithInstanceId(terminateInstance);
      FlowNode node = activityInstance.getFlowNode();
      FLowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(node);
      processor.processTerminate(
          instanceResult,
          directInstanceResult,
          activityInstance,
          processInstance,
          variables,
          flowNodeInstances);
    }

    while (!directInstanceResult.newFlowNodeInstancesIsEmpty()) {
      FlowNodeInstanceInfo instanceInfo = directInstanceResult.pollNewFlowNodeInstance();
      FlowNodeInstance<?> fLowNodeInstance = instanceInfo.flowNodeInstance();
      flowNodeInstances.putInstance(fLowNodeInstance);
      FLowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(fLowNodeInstance.getFlowNode());
      processor.processStart(
          instanceResult,
          directInstanceResult,
          flowElements,
          instanceInfo.flowNodeInstance(),
          processInstance,
          instanceInfo.inputSequenceFlowId(),
          variables,
          false,
          flowNodeInstances);
    }
  }

  private void processEventByFlowNodeInstance(
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      ProcessInstance processInstance,
      EventSignal event,
      FlowNodeInstance fLowNodeInstance,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      Variables variables) {

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
                flowNodeInstances);
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
                  flowNodeInstances);
          if (eventHandled) {
            break;
          }
        }
      }
    }

    // Still not handled, bubble up if so defined
    if (!eventHandled && event.bubbleUp() && fLowNodeInstance.getParentInstance() != null) {
      event.selectParent();
      directInstanceResult.addEvent(event);
      while (directInstanceResult.hasDirectTriggers()) {
        processDirectTriggers(
            flowNodeInstances.getParentFlowNodeInstances(),
            processInstance,
            instanceResult,
            directInstanceResult,
            flowElements.getParentElements(),
            variables);
      }
    } else if (!eventHandled && event.bubbleUp() && fLowNodeInstance.getParentInstance() == null) {
      // Still not handled and No more bubbling up possible
      directInstanceResult.addTerminateInstance(event.getSourceInstance().getElementInstanceId());
    }
  }
}
