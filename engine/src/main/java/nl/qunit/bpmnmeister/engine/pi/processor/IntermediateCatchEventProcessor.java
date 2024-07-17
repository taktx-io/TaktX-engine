package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
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
  protected void triggerCatchEventStart(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      IntermediateCatchEvent element,
      IntermediateCatchEventState oldState,
      ScopedVars variables) {
    IntermediateCatchEventStateBuilder<?, ?> newStateBuilder = oldState.toBuilder();

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
    triggerResultBuilder.newFlowNodeStates(List.of(newStateBuilder.build())).build();
  }

  @Override
  protected IntermediateCatchEventState getTerminateElementState(
      IntermediateCatchEventState elementState) {
    return new IntermediateCatchEventState(
        elementState.getElementInstanceId(),
        elementState.getElementId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getScheduledKeys(),
        elementState.getInputFlowId());
  }

  @Override
  protected void triggerEventContinue(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      TriggerResultBuilder triggerResultBuilder,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      IntermediateCatchEvent element,
      IntermediateCatchEventState oldState,
      ScopedVars variables) {
    IntermediateCatchEventStateBuilder<?, ?> newStateBuilder = oldState.toBuilder();
    if (!element.getMessageventDefinitions().isEmpty()) {
      catchEventMessageHelper.processWhenActive(
          continueFlowElementTrigger,
          triggerResultBuilder,
          newStateBuilder,
          element,
          oldState,
          processInstance,
          processDefinition,
          variables);
    } else if (!element.getTimerEventDefinitions().isEmpty()) {
      catchEventSchedulerHelper.processWhenActive(
          continueFlowElementTrigger,
          triggerResultBuilder,
          newStateBuilder,
          element,
          oldState,
          processInstance,
          processDefinition);
    }
    triggerResultBuilder.newFlowNodeStates(List.of(newStateBuilder.build()));
  }
}
