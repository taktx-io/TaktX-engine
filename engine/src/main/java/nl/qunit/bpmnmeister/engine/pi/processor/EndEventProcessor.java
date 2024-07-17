package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.EndEventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@ApplicationScoped
public class EndEventProcessor extends ThrowEventProcessor<EndEvent, EndEventState> {

  @Override
  protected void triggerThrowEvent(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      EndEvent element,
      EndEventState oldState,
      ScopedVars variables) {
    EndEventState newState =
        new EndEventState(
            oldState.getElementInstanceId(),
            oldState.getElementId(),
            oldState.getPassedCnt() + 1,
            FlowNodeStateEnum.FINISHED,
            oldState.getInputFlowId());
    triggerResultBuilder.newFlowNodeStates(List.of(newState)).build();
  }

  @Override
  protected EndEventState getTerminateElementState(EndEventState elementState) {
    return new EndEventState(
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
