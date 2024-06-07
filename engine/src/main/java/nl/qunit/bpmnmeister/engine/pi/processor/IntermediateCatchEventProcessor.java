package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState.IntermediateCatchEventStateBuilder;

@ApplicationScoped
public class IntermediateCatchEventProcessor
    extends CatchEventProcessor<IntermediateCatchEvent, IntermediateCatchEventState> {

  @Inject MessageCatchEventHelper catchEventMessageHelper;
  @Inject CatchEventSchedulerHelper catchEventSchedulerHelper;

  @Override
  protected TriggerResult triggerCatchEvent(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      IntermediateCatchEvent element,
      IntermediateCatchEventState oldState,
      Variables variables) {
    TriggerResultBuilder triggerResultBuilder = TriggerResult.builder();
    IntermediateCatchEventStateBuilder<?, ?> newStateBuilder = oldState.toBuilder();

    if (oldState.getState() == FlowNodeStateEnum.READY) {
      catchEventMessageHelper.processWhenReady(
          processDefinition,
          triggerResultBuilder,
          newStateBuilder,
          processInstance,
          element,
          variables);
      catchEventSchedulerHelper.processWhenReady(
          triggerResultBuilder, newStateBuilder, processInstance, element, oldState);
    } else if (oldState.getState() == FlowNodeStateEnum.ACTIVE) {
      catchEventMessageHelper.processWhenActive(
          triggerResultBuilder, newStateBuilder, element, oldState);
      catchEventSchedulerHelper.processWhenActive(
          trigger, triggerResultBuilder, newStateBuilder, element, oldState);
    }

    return triggerResultBuilder.newFlowNodeState(newStateBuilder.build()).build();
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
