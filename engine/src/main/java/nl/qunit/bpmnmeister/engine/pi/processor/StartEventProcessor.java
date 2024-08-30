package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.StartEventDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.StartThrowingEvent;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.StartEventState;

@ApplicationScoped
public class StartEventProcessor extends CatchEventProcessor<StartEventDTO, StartEventState> {

  @Override
  protected void triggerCatchEventStart(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO processDefinition,
      StartEventDTO element,
      StartEventState oldState,
      ScopedVars variables) {
    triggerResultBuilder
        .newFlowNodeStates(
            List.of(
                new StartEventState(
                    oldState.getElementInstanceId(),
                    oldState.getElementId(),
                    oldState.getPassedCnt() + 1,
                    FlowNodeStateEnum.FINISHED,
                    oldState.getInputFlowId())))
        .processInstanceTriggers(
            TriggerHelper.getProcessInstanceTriggersForOutputFlows(
                processInstance, processDefinition, oldState, element))
        .throwingEvent(new StartThrowingEvent())
        .build();
  }

  @Override
  protected StartEventState getTerminateElementState(StartEventState elementState) {
    return new StartEventState(
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
