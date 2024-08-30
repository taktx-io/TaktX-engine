package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElementDTO;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.FlowNodeDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.SequenceFlowDTO;
import nl.qunit.bpmnmeister.pd.model.SubProcessDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;

public class TriggerHelper {
  private TriggerHelper() {}

  public static List<ProcessInstanceTrigger> getProcessInstanceTriggersForOutputFlows(
      ProcessInstance processInstance,
      ProcessDefinitionDTO processDefinition,
      FlowNodeStateDTO state,
      FlowNodeDTO element) {
    FlowElementsDTO flowElements = processDefinition
        .getDefinitions()
        .getRootProcess()
        .getFlowElements();
    if (!element.getParentId().equals(Constants.NONE)) {
      String[] parentIds = element.getParentId().split("/");
      for (String parentId : parentIds) {
        Optional<FlowElementDTO> flowElement = flowElements.getFlowElement(parentId);
        if (flowElement.isPresent() && flowElement.get() instanceof SubProcessDTO subProcess) {
          flowElements = subProcess.getElements();
        }
      }
    }

    List<SequenceFlowDTO> outgoingSequenceFlowsForElement =
        flowElements
            .getOutgoingSequenceFlowsForElement(element);
    return outgoingSequenceFlowsForElement.stream()
        .map(
            sequenceFlow -> {
              String targetElementId = element.getParentId() + "/" + sequenceFlow.getTarget();
              return new StartFlowElementTrigger(
                  processInstance.getProcessInstanceKey(),
                  state.getElementInstanceId(),
                  targetElementId,
                  sequenceFlow.getId(),
                  VariablesDTO.empty());
            })
        .collect(Collectors.toList());
  }
}
