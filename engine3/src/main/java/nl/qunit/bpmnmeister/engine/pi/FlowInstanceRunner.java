package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.processor.FLowNodeInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessInstanceProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@ApplicationScoped
@RequiredArgsConstructor
public class FlowInstanceRunner {

  private final ProcessInstanceProcessorProvider processInstanceProcessorProvider;

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

    List<UUID> terminateInstances = instanceResult.getTerminateInstances();
    for (UUID terminateInstance : terminateInstances) {
      FLowNodeInstance<?> activityInstance =
          flowNodeInstances.getInstanceWithInstanceId(terminateInstance);
      FlowNode node = activityInstance.getFlowNode();
      FLowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(node);
      newInstanceResult.merge(processor.processTerminate(activityInstance));
    }

    List<FLowNodeInstanceInfo> newFlowNodeInstances = instanceResult.getNewFlowNodeInstanceInfos();
    for (FLowNodeInstanceInfo instanceInfo : newFlowNodeInstances) {
      FLowNodeInstance<?> fLowNodeInstance = instanceInfo.flowNodeInstance();
      flowNodeInstances.putInstance(fLowNodeInstance);
      FlowNode node =
          flowElements.getFlowNode(fLowNodeInstance.getFlowNode().getId()).orElseThrow();
      FLowNodeInstanceProcessor<?, ?, ?> processor =
          processInstanceProcessorProvider.getProcessor(node);
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
}
