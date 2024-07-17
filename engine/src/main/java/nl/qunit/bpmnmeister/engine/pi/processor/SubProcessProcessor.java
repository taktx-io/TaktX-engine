package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.SubProcessState;

@ApplicationScoped
public class SubProcessProcessor extends ActivityProcessor<SubProcess, SubProcessState> {

  @Override
  protected TriggerResult triggerStartFlowElementWithoutLoop(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      SubProcess element,
      SubProcessState oldState,
      ScopedVars variables) {
    List<ProcessInstanceTrigger> subProcessTriggers = new ArrayList<>();
    String startElement = getStartEvent(element);
    UUID childProcessInstanceKey = UUID.randomUUID();
    UUID parentProcessInstanceKey = processInstance.getProcessInstanceKey();
    variables.push(childProcessInstanceKey, parentProcessInstanceKey, Variables.empty());
    StartNewProcessInstanceTrigger subProcessTrigger =
        new StartNewProcessInstanceTrigger(
            processInstance.getRootInstanceKey(),
            childProcessInstanceKey,
            parentProcessInstanceKey,
            element.getAsSubProcessDefinition(definition),
            element.getId(),
            oldState.getElementInstanceId(),
            startElement,
            Constants.NONE,
            variables.getCurrentScopeVariables());
    subProcessTriggers.add(subProcessTrigger);
    SubProcessState newSubProcessState =
        new SubProcessState(
            FlowNodeStateEnum.ACTIVE,
            childProcessInstanceKey,
            oldState.getParentElementInstanceId(),
            oldState.getElementInstanceId(),
            oldState.getElementId(),
            oldState.getPassedCnt(),
            oldState.getLoopCnt(),
            oldState.getInputFlowId());
    return TriggerResult.builder()
        .newFlowNodeStates(List.of(newSubProcessState))
        .processInstanceTriggers(subProcessTriggers)
        .build();
  }

  @Override
  protected TriggerResult triggerContinueFlowElement(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      SubProcess element,
      SubProcessState subProcessState,
      ScopedVars variables) {
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
    return finishActivity(
        TriggerResult.EMPTY,
        processInstance,
        definition,
        element,
        newSubProcessState,
        ScopedVars.EMPTY);
  }

  private String getStartEvent(SubProcess subProcess) {
    if (!subProcess.getElements().getStartEvents().isEmpty()) {
      return subProcess.getElements().getStartEvents().get(0).getId();
    } else {
      return subProcess.getElements().values().get(0).getId();
    }
  }

  @Override
  public TriggerResult terminate(
      TerminateTrigger terminateTrigger, SubProcess flowElement, SubProcessState elementState) {
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
