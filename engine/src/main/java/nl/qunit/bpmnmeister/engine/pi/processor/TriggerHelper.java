package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.List;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

public class TriggerHelper {
  private TriggerHelper() {}

  public static List<ProcessInstanceTrigger> getProcessInstanceTriggersForOutputFlows(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      FlowNodeState state,
      FlowNode element) {
    List<SequenceFlow> outgoingSequenceFlowsForElement =
        processDefinition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getOutgoingSequenceFlowsForElement(element);
    return outgoingSequenceFlowsForElement.stream()
        .map(
            sequenceFlow -> {
              String targetElementId = sequenceFlow.getTarget();
              return new StartFlowElementTrigger(
                  processInstance.getProcessInstanceKey(),
                  state.getElementInstanceId(),
                  targetElementId,
                  sequenceFlow.getId(),
                  Variables.empty());
            })
        .collect(Collectors.toList());
  }
}
