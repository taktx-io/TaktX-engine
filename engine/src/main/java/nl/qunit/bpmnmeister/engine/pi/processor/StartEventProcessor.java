package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartThrowingEvent;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.StartEventState;

@ApplicationScoped
public class StartEventProcessor extends CatchEventProcessor<StartEvent, StartEventState> {

  @Override
  protected void triggerCatchEvent(
      TriggerResultBuilder triggerResultBuilder,
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      StartEvent element,
      StartEventState oldState,
      ScopedVars variables) {
    triggerResultBuilder
        .newFlowNodeState(
            new StartEventState(
                oldState.getElementInstanceId(),
                oldState.getPassedCnt() + 1,
                FlowNodeStateEnum.FINISHED,
                oldState.getInputFlowId()))
        .processInstanceTriggers(
            TriggerHelper.getProcessInstanceTriggersForOutputFlows(
                processInstance, processDefinition, element))
        .throwingEvent(new StartThrowingEvent())
        .build();
  }

  @Override
  protected StartEventState getTerminateElementState(StartEventState elementState) {
    return new StartEventState(
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
