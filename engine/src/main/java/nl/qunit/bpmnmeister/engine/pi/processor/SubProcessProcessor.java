package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowNodeDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.SubProcessDTO;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.SubProcessState;

@ApplicationScoped
public class SubProcessProcessor extends ActivityProcessor<SubProcessDTO, SubProcessState> {

  @Override
  protected TriggerResult triggerStartFlowElement(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      SubProcessDTO element,
      SubProcessState oldState,
      ScopedVars variables) {

    Optional<FlowNodeDTO> optStartElement = getStartEvent(element);
    if (optStartElement.isEmpty()) {
      return TriggerResult.builder()
          .newFlowNodeStates(List.of(getFinishedSubProcessState(oldState)))
          .processInstanceTriggers(List.of())
          .build();
    }

    FlowNodeDTO startElement = optStartElement.get();
    String parentPrefix =
        startElement.getParentId().equals(Constants.NONE) ? "" : startElement.getParentId() + "/";

    StartFlowElementTrigger startSubProcessTrigger =
        new StartFlowElementTrigger(
            processInstance.getProcessInstanceKey(),
            oldState.getElementInstanceId(),
            parentPrefix + startElement.getId(),
            Constants.NONE,
            variables.getCurrentScopeVariables());

    SubProcessState newSubProcessState =
        new SubProcessState(
            FlowNodeStateEnum.WAITING,
            UUID.randomUUID(),
            oldState.getParentElementInstanceId(),
            oldState.getElementInstanceId(),
            oldState.getElementId(),
            oldState.getPassedCnt(),
            oldState.getLoopCnt(),
            oldState.getInputFlowId());
    return TriggerResult.builder()
        .newFlowNodeStates(List.of(newSubProcessState))
        .processInstanceTriggers(List.of(startSubProcessTrigger))
        .build();
  }

  @Override
  protected TriggerResult triggerContinueFlowElement(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      SubProcessDTO element,
      SubProcessState subProcessState,
      ScopedVars variables) {
    SubProcessState newSubProcessState = getFinishedSubProcessState(
        subProcessState);
    return finishActivity(
        TriggerResult.EMPTY,
        processInstance,
        definition,
        element,
        newSubProcessState,
        ScopedVars.EMPTY);
  }

  private static SubProcessState getFinishedSubProcessState(SubProcessState subProcessState) {
    SubProcessState newSubProcessState =
        new SubProcessState(
            FlowNodeStateEnum.FINISHED,
            subProcessState.getChildProcessInstanceId(),
            subProcessState.getParentElementInstanceId(),
            subProcessState.getElementInstanceId(),
            subProcessState.getElementId(),
            subProcessState.getPassedCnt() + 1,
            subProcessState.getLoopCnt(),
            subProcessState.getInputFlowId());
    return newSubProcessState;
  }

  private Optional<FlowNodeDTO> getStartEvent(SubProcessDTO subProcess) {
    if (!subProcess.getElements().getStartEvents().isEmpty()) {
      return Optional.of(subProcess.getElements().getStartEvents().get(0));
    } else {
      // get the first element without incoming flow. If that is not available
      // get the first element. If no element is available return optional empty.
      return subProcess.getElements().getFlowNodes().stream()
          .filter(flowNode -> flowNode.getIncoming().isEmpty())
          .findFirst();
    }
  }

  @Override
  public TriggerResult terminate(
      TerminateTrigger terminateTrigger, SubProcessDTO flowElement, SubProcessState elementState) {
    ProcessInstanceTrigger terminateSubProcessTrigger =
        new TerminateTrigger(
            elementState.getChildProcessInstanceId(), Constants.NONE, Constants.NONE_UUID);
    return TriggerResult.builder()
        .newFlowNodeStates(List.of(getTerminateElementState(elementState)))
        .processInstanceTriggers(List.of(terminateSubProcessTrigger))
        .build();
  }

  @Override
  protected SubProcessState getTerminateElementState(SubProcessState elementState) {
    return new SubProcessState(
        FlowNodeStateEnum.TERMINATED,
        elementState.getChildProcessInstanceId(),
        elementState.getParentElementInstanceId(),
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        elementState.getLoopCnt(),
        elementState.getInputFlowId());
  }
}
