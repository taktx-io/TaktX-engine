package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.IntermediateThrowEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.IntermediateThrowEventState;

@ApplicationScoped
public class IntermediateThrowEventProcessor
    extends ThrowEventProcessor<IntermediateThrowEvent, IntermediateThrowEventState> {

  @Override
  protected void triggerThrowEvent(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO processDefinition,
      IntermediateThrowEvent element,
      IntermediateThrowEventState oldState,
      ScopedVars variables) {
    IntermediateThrowEventState newState =
        new IntermediateThrowEventState(
            oldState.getElementInstanceId(),
            oldState.getElementId(),
            oldState.getPassedCnt() + 1,
            FlowNodeStateEnum.FINISHED,
            oldState.getInputFlowId());
    triggerResultBuilder.newFlowNodeStates(List.of(newState));
  }

  @Override
  protected IntermediateThrowEventState getTerminateElementState(
      IntermediateThrowEventState elementState) {
    return new IntermediateThrowEventState(
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getInputFlowId());
  }
}
