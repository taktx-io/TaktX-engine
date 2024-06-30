package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState.IntermediateCatchEventStateBuilder;

@ApplicationScoped
public class IntermediateCatchEventProcessor
    extends CatchEventProcessor<IntermediateCatchEvent, IntermediateCatchEventState> {

  @Inject MessageCatchEventHelper catchEventMessageHelper;
  @Inject LinkCatchEventHelper catchEventLinkHelper;
  @Inject CatchEventSchedulerHelper catchEventSchedulerHelper;

  @Override
  protected void triggerCatchEvent(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      IntermediateCatchEvent element,
      IntermediateCatchEventState oldState,
      ScopedVars variables) {
    IntermediateCatchEventStateBuilder<?, ?> newStateBuilder = oldState.toBuilder();

    if (oldState.getState() == FlowNodeStateEnum.READY) {
      if (!element.getLinkventDefinitions().isEmpty()) {
        catchEventLinkHelper.processWhenReady(
            processDefinition,
            triggerResultBuilder,
            newStateBuilder,
            processInstance,
            element,
            variables,
            oldState);
      } else if (!element.getMessageventDefinitions().isEmpty()) {
        catchEventMessageHelper.processWhenReady(
            processDefinition,
            triggerResultBuilder,
            newStateBuilder,
            processInstance,
            element,
            variables);
      } else if (!element.getTimerEventDefinitions().isEmpty()) {
        catchEventSchedulerHelper.processWhenReady(
            triggerResultBuilder, newStateBuilder, processInstance, element, oldState, variables);
      }
    } else if (oldState.getState() == FlowNodeStateEnum.ACTIVE) {
      catchEventMessageHelper.processWhenActive(
          trigger,
          triggerResultBuilder,
          newStateBuilder,
          element,
          oldState,
          processInstance,
          processDefinition,
          variables);
      catchEventSchedulerHelper.processWhenActive(
          trigger,
          triggerResultBuilder,
          newStateBuilder,
          element,
          oldState,
          processInstance,
          processDefinition);
    }

    triggerResultBuilder.newFlowNodeState(newStateBuilder.build()).build();
  }

  @Override
  protected IntermediateCatchEventState getTerminateElementState(
      IntermediateCatchEventState elementState) {
    return new IntermediateCatchEventState(
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getScheduledKeys(),
        elementState.getInputFlowId());
  }
}
