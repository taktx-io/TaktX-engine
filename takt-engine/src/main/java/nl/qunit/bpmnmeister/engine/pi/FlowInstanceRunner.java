package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.processor.BoundaryEventInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.FLowNodeInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.EventSignal;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.BoundaryEventInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

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
      FLowNodeInstance<?> activityInstance =
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
      FLowNodeInstanceInfo instanceInfo = directInstanceResult.pollNewFlowNodeInstance();
      FLowNodeInstance<?> fLowNodeInstance = instanceInfo.flowNodeInstance();
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
      FLowNodeInstance fLowNodeInstance,
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
