package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.Event;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.EventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Slf4j
public abstract class EventProcessor<E extends Event<?>, S extends EventState>
    extends StateProcessor<E, S> {

  @Inject protected FeelExpressionHandler feelExpressionHandler;
  @Inject protected IoMappingProcessor ioMappingProcessor;

  public final TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      FlowNode<?> element,
      ScopedVars variables) {
    log.info("Trigger processor: " + this);

    if (trigger instanceof StartFlowElementTrigger flowElementTrigger) {
      Event event = (Event) element;
      FlowNodeState flowNodeState =
          event.getInitialState(
              flowElementTrigger.getElementId(), flowElementTrigger.getInputFlowId(), 0);
      return triggerStartFlowElement(
          flowElementTrigger,
          processInstance,
          definition,
          (E) element,
          (S) flowNodeState,
          variables);
    } else if (trigger instanceof ContinueFlowElementTrigger continueFlowElementTrigger) {
      Optional<FlowNodeState> flowNodeState =
          processInstance
              .getFlowNodeStates()
              .get(continueFlowElementTrigger.getElementInstanceId());
      if (flowNodeState.isPresent()) {
        TriggerResult triggerResult =
            triggerContinueFlowElement(
                continueFlowElementTrigger,
                processInstance,
                definition,
                (E) element,
                (S) flowNodeState.get(),
                variables);
        return triggerResult;
      } else {
        return TriggerResult.EMPTY;
      }
    } else if (trigger instanceof TerminateTrigger terminateTrigger) {
      Optional<FlowNodeState> flowNodeState =
          processInstance.getFlowNodeStates().get(terminateTrigger.getElementInstanceId());
      if (flowNodeState.isPresent() && flowNodeState.get().getState() == FlowNodeStateEnum.ACTIVE) {
        return terminate(terminateTrigger, (E) element, (S) flowNodeState.get());
      } else {
        return TriggerResult.EMPTY;
      }
    }
    throw new IllegalStateException("Unknown trigger type: " + trigger);
  }

  public TriggerResult terminate(TerminateTrigger terminateTrigger, E flowElement, S elementState) {
    return TriggerResult.builder()
        .newFlowNodeStates(List.of(getTerminateElementState(elementState)))
        .build();
  }

  protected abstract S getTerminateElementState(S elementState);

  public TriggerResult triggerStartFlowElement(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      ScopedVars variables) {
    TriggerResultBuilder triggerResultBuilder = TriggerResult.builder();
    UUID childProcessInstanceKey = UUID.randomUUID();
    variables.push(
        childProcessInstanceKey, processInstance.getProcessInstanceKey(), trigger.getVariables());
    Variables outputVariables = ioMappingProcessor.getOutputVariables(element, variables);
    variables.pop();
    variables.merge(outputVariables);

    triggerEventStart(
        triggerResultBuilder, trigger, processInstance, definition, element, oldState, variables);
    return triggerResultBuilder.build();
  }

  protected TriggerResult triggerContinueFlowElement(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S s,
      ScopedVars variables) {
    TriggerResultBuilder triggerResultBuilder = TriggerResult.builder();
    triggerEventContinue(
        continueFlowElementTrigger,
        triggerResultBuilder,
        processInstance,
        definition,
        element,
        s,
        variables);
    return triggerResultBuilder.build();
  }

  protected abstract void triggerEventContinue(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      TriggerResultBuilder triggerResultBuilder,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables);

  protected abstract void triggerEventStart(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables);
}
