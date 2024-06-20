package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.EndThrowingEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.EndEventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class EndEventProcessor extends ThrowEventProcessor<EndEvent, EndEventState> {

  @Override
  protected void triggerThrowEvent(
      TriggerResultBuilder triggerResultBuilder,
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      EndEvent element,
      EndEventState oldState,
      ScopedVars variables) {
    List<ProcessInstanceTrigger> processInstanceTriggers = new ArrayList<>();
    if (!processInstance.getParentInstanceKey().equals(ProcessInstanceKey.NONE)) {
      processInstanceTriggers.add(
          new FlowElementTrigger(
              processInstance.getParentInstanceKey(),
              processInstance.getParentElementId(),
              Constants.NONE,
              variables.getCurrentScopeVariables()));
    }
    EndEventState newState =
        new EndEventState(
            oldState.getElementInstanceId(),
            oldState.getPassedCnt() + 1,
            FlowNodeStateEnum.FINISHED,
            oldState.getInputFlowId());
    triggerResultBuilder
        .newFlowNodeState(newState)
        .processInstanceTriggers(processInstanceTriggers)
        .throwingEvent(new EndThrowingEvent())
        .build();
  }

  @Override
  protected EndEventState getTerminateElementState(EndEventState elementState) {
    return new EndEventState(
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
