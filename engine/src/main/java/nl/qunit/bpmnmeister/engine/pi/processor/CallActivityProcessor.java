package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.CallActivity;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.CallActivityState;

@ApplicationScoped
public class CallActivityProcessor extends ActivityProcessor<CallActivity, CallActivityState> {

  @Override
  protected TriggerResult triggerFlowElementWithoutLoop(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      CallActivity element,
      CallActivityState oldState,
      Variables variables) {
    if (oldState.getState() == ActivityStateEnum.READY) {
      return new TriggerResult(
          new CallActivityState(
              ActivityStateEnum.ACTIVE,
              oldState.getElementInstanceId(),
              oldState.getPassedCnt(),
              oldState.getLoopCnt()),
          Set.of(),
          Set.of(),
          Set.of(),
          Set.of(
              new StartCommand(
                  processInstance.getProcessInstanceKey(),
                  element.getId(),
                  element.getCalledElement(),
                  variables)),
          ThrowingEvent.NOOP,
          Set.of(),
          Variables.EMPTY);
    } else if (oldState.getState() == ActivityStateEnum.ACTIVE) {
      CallActivityState newState =
          new CallActivityState(
              ActivityStateEnum.FINISHED,
              oldState.getElementInstanceId(),
              oldState.getPassedCnt() + 1,
              oldState.getLoopCnt());
      return finishActivity(processInstance, element, newState, variables);
    } else {
      return new TriggerResult(
          oldState,
          Set.of(),
          Set.of(),
          Set.of(),
          Set.of(),
          ThrowingEvent.NOOP,
          Set.of(),
          Variables.EMPTY);
    }
  }
}
