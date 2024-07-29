package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.engine.pi.processor.flowelement.BoundaryEventProcessor;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTriggerIteration;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import nl.qunit.bpmnmeister.pi.state.BoundaryEventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@Slf4j
public abstract class ActivityProcessor<E extends Activity, S extends ActivityState>
    extends StateProcessor<E, S> {

  @Inject FeelExpressionHandler feelExpressionHandler;
  @Inject BoundaryEventProcessor boundaryEventProcessor;

  public final TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      FlowNode<?> element,
      ScopedVars variables) {
    log.info("Trigger activity processor: " + this);
    TriggerResult triggerResult;
    S oldState = null;
    if (trigger instanceof StartFlowElementTriggerIteration flowElementTriggerIteration) {
      oldState =
          (S)
              ((E) element)
                  .getInitialState(
                      flowElementTriggerIteration.getParentElementInstance(),
                      element.getId(),
                      flowElementTriggerIteration.getInputFlowId(),
                      0);
      triggerResult =
          triggerStartFlowElement(
              flowElementTriggerIteration,
              processInstance,
              definition,
              (E) element,
              oldState,
              variables);
    } else if (trigger instanceof StartFlowElementTrigger flowElementTrigger) {
      oldState =
          (S)
              ((E) element)
                  .getInitialState(
                      Constants.NONE_UUID, element.getId(), flowElementTrigger.getInputFlowId(), 0);
      triggerResult =
          triggerStartFlowElement(
              flowElementTrigger, processInstance, definition, (E) element, oldState, variables);
    } else if (trigger instanceof ContinueFlowElementTrigger continueFlowElementTrigger) {
      Optional<FlowNodeState> flowNodeState =
          processInstance
              .getFlowNodeStates()
              .get(continueFlowElementTrigger.getElementInstanceId());
      if (flowNodeState.isPresent()) {
        oldState = (S) flowNodeState.get();
        triggerResult =
            triggerContinueFlowElement(
                continueFlowElementTrigger,
                processInstance,
                definition,
                (E) element,
                oldState,
                variables);
      } else {
        triggerResult = TriggerResult.EMPTY;
      }
    } else if (trigger instanceof TerminateTrigger terminateTrigger) {
      Optional<FlowNodeState> flowNodeState =
          processInstance.getFlowNodeStates().get(terminateTrigger.getElementInstanceId());
      if (flowNodeState.isPresent() && flowNodeState.get().getState() == FlowNodeStateEnum.ACTIVE) {
        oldState = (S) flowNodeState.get();
        triggerResult = terminate(terminateTrigger, (E) element, oldState);
      } else {
        triggerResult = TriggerResult.EMPTY;
      }
    } else {
      triggerResult = TriggerResult.EMPTY;
    }

    List<ProcessInstanceTrigger> triggersForBoundaryEvents =
        getTriggerResultForBoundaryEvents(
            processInstance, definition, element, oldState, triggerResult);
    List<ProcessInstanceTrigger> allProcessInstanceTriggers =
        new ArrayList<>(triggerResult.getProcessInstanceTriggers());
    allProcessInstanceTriggers.addAll(triggersForBoundaryEvents);
    return triggerResult.toBuilder().processInstanceTriggers(allProcessInstanceTriggers).build();
  }

  protected abstract TriggerResult triggerContinueFlowElement(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S s,
      ScopedVars variables);

  protected TriggerResult terminate(
      TerminateTrigger terminateTrigger, E flowElement, S elementState) {
    return TriggerResult.builder()
        .newFlowNodeStates(List.of(getTerminateElementState(elementState)))
        .build();
  }

  protected abstract S getTerminateElementState(S elementState);

  protected abstract TriggerResult triggerStartFlowElement(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      ScopedVars variables);

  protected TriggerResult finishActivity(
      TriggerResult triggerResult,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity<?> element,
      ActivityState newState,
      ScopedVars variables) {
    List<ProcessInstanceTrigger> triggers =
        TriggerHelper.getProcessInstanceTriggersForOutputFlows(
            processInstance, processDefinition, newState, element);

    return triggerResult.toBuilder()
        .newFlowNodeStates(List.of(newState))
        .processInstanceTriggers(triggers)
        .build();
  }

  private List<ProcessInstanceTrigger> getTriggerResultForBoundaryEvents(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      FlowNode element,
      S oldState,
      TriggerResult triggerResult) {
    if (triggerResult.getNewFlowNodeStates().isEmpty()) {
      return List.of();
    }
    S newElementState = (S) triggerResult.getNewFlowNodeStates().get(0);
    List<ProcessInstanceTrigger> triggers = new ArrayList<>();
    if (elementActivated(oldState, newElementState)) {
      List<BoundaryEvent> boundaryEvents =
          definition
              .getDefinitions()
              .getRootProcess()
              .getFlowElements()
              .getBoundaryEventsAttachedToElement(element.getId());
      for (BoundaryEvent boundaryEvent : boundaryEvents) {
        triggers.add(
            new StartFlowElementTrigger(
                processInstance.getProcessInstanceKey(),
                oldState.getElementInstanceId(),
                boundaryEvent.getId(),
                Constants.NONE,
                Variables.empty()));
      }
    } else if (elementFinished(oldState, newElementState)) {
      // Find all boundary event instances attached to the element instance
      processInstance
          .getFlowNodeStates()
          .filter(
              fn ->
                  fn instanceof BoundaryEventState boundaryEventState
                      && boundaryEventState
                          .getAttachedInstanceId()
                          .equals(newElementState.getElementInstanceId()))
          .stream()
          .map(fn -> (BoundaryEventState) fn)
          .forEach(
              bes -> {
                triggers.add(
                    new TerminateTrigger(
                        processInstance.getProcessInstanceKey(),
                        bes.getElementId(),
                        bes.getElementInstanceId()));
              });
    }
    return triggers;
  }

  private boolean elementActivated(S oldState, S newState) {
    return oldState.getState() == FlowNodeStateEnum.READY
        && newState.getState() == FlowNodeStateEnum.ACTIVE;
  }

  protected boolean elementFinished(S oldState, S newState) {
    return oldState.getState() == FlowNodeStateEnum.ACTIVE && newState.getState().isFinished();
  }
}
