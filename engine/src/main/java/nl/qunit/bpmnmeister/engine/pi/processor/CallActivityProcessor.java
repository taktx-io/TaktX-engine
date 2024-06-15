package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.state.CallActivityState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class CallActivityProcessor extends ActivityProcessor<CallActivity, CallActivityState> {

  @Override
  protected TriggerResult triggerFlowElementWithoutLoop(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      CallActivity element,
      CallActivityState oldState,
      ScopedVars variables) {
    if (oldState.getState() == FlowNodeStateEnum.READY) {
      return TriggerResult.builder()
          .newFlowNodeState(
              new CallActivityState(
                  FlowNodeStateEnum.ACTIVE,
                  oldState.getElementInstanceId(),
                  oldState.getPassedCnt(),
                  oldState.getLoopCnt(),
                  oldState.getInputFlowId()))
          .newStartCommands(
              Set.of(
                  new StartCommand(
                      processInstance.getRootInstanceKey(),
                      processInstance.getProcessInstanceKey(),
                      Constants.NONE,
                      element.getId(),
                      element.getCalledElement(),
                      variables.getCurrentScopeVariables())))
          .build();
    } else if (oldState.getState() == FlowNodeStateEnum.ACTIVE) {
      CallActivityState newState =
          new CallActivityState(
              FlowNodeStateEnum.FINISHED,
              oldState.getElementInstanceId(),
              oldState.getPassedCnt() + 1,
              oldState.getLoopCnt(),
              oldState.getInputFlowId());
      return finishActivity(processInstance, definition, element, newState, variables);
    } else {
      return TriggerResult.builder().newFlowNodeState(oldState).build();
    }
  }

  @Override
  protected CallActivityState getTerminateElementState(CallActivityState elementState) {
    return new CallActivityState(
        FlowNodeStateEnum.TERMINATED,
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        elementState.getLoopCnt(),
        elementState.getInputFlowId());
  }
}
