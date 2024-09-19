package nl.qunit.bpmnmeister.engine.pi;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.processor.FLowNodeInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessInstanceProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@ApplicationScoped
@RequiredArgsConstructor
public class FlowInstanceRunner {

  private final ProcessInstanceProcessorProvider processInstanceProcessorProvider;

  public InstanceResult processInstanceResult(
      FlowNodeStates2 flowNodeStates2,
      InstanceResult instanceResult,
      FlowElements2 flowElements,
      Variables2 variables,
      FlowNodeStates2 flowNodeStates) {
    InstanceResult newInstanceResult = new InstanceResult();
    List<FLowNodeInstanceInfo> newFlowNodeInstances = instanceResult.getNewFlowNodeInstanceInfos();
    for (FLowNodeInstanceInfo instanceInfo : newFlowNodeInstances) {
      FLowNodeInstance<?> fLowNodeInstance = instanceInfo.flowNodeInstance();
      flowNodeStates2.putInstance(fLowNodeInstance);
      FlowNode2 node =
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
              flowNodeStates);
      newInstanceResult.merge(subInstanceResult);
    }
    return newInstanceResult;
  }
}
