package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.processor.BoundaryEventInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.FLowNodeInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessInstanceProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.EventSignal;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.BoundaryEventInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@ApplicationScoped
@RequiredArgsConstructor
public class FlowInstanceRunner {

  private final ProcessInstanceProcessorProvider processInstanceProcessorProvider;
  private final BoundaryEventInstanceProcessor boundaryEventProcessor;

  public InstanceResult continueNewInstances(
      InstanceResult instanceResult,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      Variables processInstanceVariables) {
    while (instanceResult.hasDirectTriggers()) {
      instanceResult =
          processDirectTriggers(
              flowNodeInstances, instanceResult, flowElements, processInstanceVariables);
    }

    flowNodeInstances.determineImplicitCompletedState();
    return instanceResult;
  }

  private InstanceResult processDirectTriggers(
      FlowNodeInstances flowNodeInstances,
      InstanceResult instanceResult,
      FlowElements flowElements,
      Variables variables) {
    InstanceResult newInstanceResult = new InstanceResult();

    List<EventSignal> events = instanceResult.getEvents();
    for (EventSignal event : events) {
      processEventByFlowNodeInstance(
          flowNodeInstances,
          flowElements,
          event,
          event.getSourceInstance(),
          newInstanceResult,
          variables);
    }

    List<UUID> terminateInstances = instanceResult.getTerminateInstances();
    for (UUID terminateInstance : terminateInstances) {
      FLowNodeInstance<?> activityInstance =
          flowNodeInstances.getInstanceWithInstanceId(terminateInstance);
      FlowNode node = activityInstance.getFlowNode();
      FLowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(node);
      newInstanceResult.merge(processor.processTerminate(activityInstance, variables));
    }

    List<FLowNodeInstanceInfo> newFlowNodeInstances = instanceResult.getNewFlowNodeInstanceInfos();
    for (FLowNodeInstanceInfo instanceInfo : newFlowNodeInstances) {
      FLowNodeInstance<?> fLowNodeInstance = instanceInfo.flowNodeInstance();
      flowNodeInstances.putInstance(fLowNodeInstance);
      FLowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(fLowNodeInstance.getFlowNode());
      InstanceResult subInstanceResult =
          processor.processStart(
              flowElements,
              instanceInfo.flowNodeInstance(),
              instanceInfo.inputSequenceFlowId(),
              variables,
              false,
              flowNodeInstances);
      newInstanceResult.merge(subInstanceResult);
    }

    instanceResult.clearDirectTriggers();
    newInstanceResult.merge(instanceResult);
    return newInstanceResult;
  }

  private void processEventByFlowNodeInstance(
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      EventSignal event,
      FLowNodeInstance fLowNodeInstance,
      InstanceResult newInstanceResult,
      Variables variables) {

    boolean eventHandled = false;
    if (fLowNodeInstance instanceof ActivityInstance<?> activityInstance) {
      // First check for specific codes
      for (BoundaryEventInstance boundaryEventInstance :
          activityInstance.getAttachedBoundaryEventInstances()) {
        if (!eventHandled) {
          eventHandled =
              boundaryEventProcessor.processEvent(
                  boundaryEventInstance, event, newInstanceResult, variables, flowNodeInstances);
          if (eventHandled) {
            break;
          }
        }
      }

      // Check for catch all events
      if (!eventHandled) {
        for (BoundaryEventInstance boundaryEventInstance :
            activityInstance.getAttachedBoundaryEventInstances()) {
          if (!eventHandled) {
            eventHandled =
                boundaryEventProcessor.processEventCatchAll(
                    boundaryEventInstance, event, newInstanceResult, variables, flowNodeInstances);
            if (eventHandled) {
              break;
            }
          }
        }
      }
    }

    // Still not handled, bubble up if so defined
    if (!eventHandled && event.bubbleUp() && fLowNodeInstance.getParentInstance() != null) {
      event.selectParent();
      InstanceResult parentInstanceResult = new InstanceResult();
      parentInstanceResult.addEvent(event);
      while (parentInstanceResult.hasDirectTriggers()) {
        parentInstanceResult =
            processDirectTriggers(
                flowNodeInstances.getParentFlowNodeInstances(),
                parentInstanceResult,
                flowElements.getParentElements(),
                variables);
      }
    } else if (!eventHandled && event.bubbleUp() && fLowNodeInstance.getParentInstance() == null) {
      // Still not handled and No more bubbling up possible
      newInstanceResult.addTerminateInstance(event.getSourceInstance().getElementInstanceId());
    }
  }
}
