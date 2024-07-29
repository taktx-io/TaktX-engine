package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.state.CallActivityState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class CallActivityProcessor extends ActivityProcessor<CallActivity, CallActivityState> {

  @Override
  protected TriggerResult triggerStartFlowElement(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      CallActivity element,
      CallActivityState oldState,
      ScopedVars variables) {
    return TriggerResult.builder()
        .newFlowNodeStates(
            List.of(
                new CallActivityState(
                    FlowNodeStateEnum.ACTIVE,
                    oldState.getChildProcessInstanceId(),
                    oldState.getParentElementInstanceId(),
                    oldState.getElementInstanceId(),
                    oldState.getElementId(),
                    oldState.getPassedCnt(),
                    oldState.getLoopCnt(),
                    oldState.getInputFlowId())))
        .newStartCommands(
            Set.of(
                new StartCommand(
                    processInstance.getRootInstanceKey(),
                    processInstance.getProcessInstanceKey(),
                    oldState.getChildProcessInstanceId(),
                    Constants.NONE,
                    element.getId(),
                    oldState.getElementInstanceId(),
                    element.getCalledElement(),
                    variables.getCurrentScopeVariables())))
        .build();
  }

  @Override
  protected TriggerResult triggerContinueFlowElement(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      CallActivity element,
      CallActivityState oldState,
      ScopedVars variables) {
    CallActivityState newState =
        new CallActivityState(
            FlowNodeStateEnum.FINISHED,
            oldState.getChildProcessInstanceId(),
            oldState.getParentElementInstanceId(),
            oldState.getElementInstanceId(),
            oldState.getElementId(),
            oldState.getPassedCnt() + 1,
            oldState.getLoopCnt(),
            oldState.getInputFlowId());
    return finishActivity(
        TriggerResult.EMPTY, processInstance, definition, element, newState, variables);
  }

  @Override
  public TriggerResult terminate(
      TerminateTrigger terminateTrigger, CallActivity flowElement, CallActivityState elementState) {
    ProcessInstanceTrigger terminateSubProcessTrigger =
        new TerminateTrigger(
            elementState.getChildProcessInstanceId(), Constants.NONE, Constants.NONE_UUID);
    return TriggerResult.builder()
        .newFlowNodeStates(List.of(getTerminateElementState(elementState)))
        .processInstanceTriggers(List.of(terminateSubProcessTrigger))
        .build();
  }

  @Override
  protected CallActivityState getTerminateElementState(CallActivityState elementState) {
    return new CallActivityState(
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
