package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

public abstract class ActivityProcessor<E extends Activity, S extends ActivityState>
    extends StateProcessor<E, S> {

  @Inject FeelExpressionHandler feelExpressionHandler;

  @Override
  protected final TriggerResult triggerFlowElement(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      ScopedVars variables) {
    TriggerResult triggerResult =
        getTriggerResultMultiInstanceOrSingle(
            trigger, processInstance, definition, element, oldState, variables);

    return getTriggerResultForBoundaryEvents(
        processInstance, definition, element, oldState, triggerResult);
  }

  protected TriggerResult getTriggerResultForBoundaryEvents(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      TriggerResult triggerResult) {
    List<BoundaryEvent> boundaryEvents =
        definition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getBoundaryEventsAttachedToElement(element.getId());
    S newElementState = (S) triggerResult.getNewFlowNodeState();
    List<ProcessInstanceTrigger> triggers = new ArrayList<>();
    if (elementActivated(oldState, newElementState)) {
      for (BoundaryEvent boundaryEvent : boundaryEvents) {
        triggers.add(
            new StartFlowElementTrigger(
                processInstance.getProcessInstanceKey(),
                boundaryEvent.getId(),
                Constants.NONE,
                Variables.empty()));
      }
    } else if (elementFinished(oldState, newElementState)) {
      for (BoundaryEvent boundaryEvent : boundaryEvents) {
        triggers.add(
            new TerminateTrigger(processInstance.getProcessInstanceKey(), boundaryEvent.getId()));
      }
    }
    triggers.addAll(triggerResult.getProcessInstanceTriggers());

    return triggerResult.toBuilder().processInstanceTriggers(triggers).build();
  }

  private TriggerResult getTriggerResultMultiInstanceOrSingle(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      ScopedVars variables) {
    TriggerResult triggerResult;
    if (element.getLoopCharacteristics().equals(LoopCharacteristics.NONE)) {
      triggerResult =
          triggerFlowElementWithoutLoop(
              trigger, processInstance, definition, element, oldState, variables);
    } else {
      triggerResult =
          triggerFlowElementWithLoop(processInstance, definition, element, oldState, variables);
    }
    return triggerResult;
  }

  private boolean elementActivated(S oldState, S newState) {
    return oldState.getState() == FlowNodeStateEnum.READY
        && newState.getState() == FlowNodeStateEnum.ACTIVE;
  }

  protected boolean elementFinished(S oldState, S newState) {
    return oldState.getState() == FlowNodeStateEnum.ACTIVE
        && newState.getState() == FlowNodeStateEnum.FINISHED;
  }

  private TriggerResult triggerFlowElementWithLoop(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      ScopedVars variables) {
    return switch (oldState.getState()) {
      case READY -> triggerWhenReady(processInstance, definition, element, oldState, variables);
      case ACTIVE -> triggerWhenActive(processInstance, definition, element, oldState, variables);
      default -> throw new IllegalStateException("Unknown state: " + oldState.getState());
    };
  }

  private TriggerResult triggerWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables) {

    // Store the output element in the output collection
    if (element.getLoopCharacteristics().getOutputCollection() != null
        && element.getLoopCharacteristics().getOutputElement() != null) {
      ArrayNode outputCollection =
          (ArrayNode) variables.get(element.getLoopCharacteristics().getOutputCollection());
      JsonNode outputElementNode =
          feelExpressionHandler.processFeelExpression(
              element.getLoopCharacteristics().getOutputElement(), variables);
      if (outputElementNode != null) {
        outputCollection.add(outputElementNode);
      }
      variables.put(element.getLoopCharacteristics().getOutputCollection(), outputCollection);
    }

    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            element.getLoopCharacteristics().getInputCollection(), variables);
    if (oldState.getLoopCnt() < inputCollection.size()) {
      List<ProcessInstanceTrigger> subProcessTriggers =
          getSubProcessTriggersWhenActive(
              processInstance,
              processDefinition,
              element,
              variables,
              inputCollection,
              oldState.getLoopCnt());

      return TriggerResult.builder()
          .newFlowNodeState(oldState.getNextLoopState())
          .processInstanceTriggers(subProcessTriggers)
          .build();
    } else {
      return finishActivity(
          processInstance, processDefinition, element, oldState.getFinishedLoopState(), variables);
    }
  }

  private List<ProcessInstanceTrigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity<?> element,
      ScopedVars variables,
      JsonNode inputCollection,
      int loopsReceived) {
    if (element.getLoopCharacteristics().isSequential()) {
      return SequentialMultiInstanceProcessor.getSubProcessTriggersWhenActive(
          processInstance, processDefinition, element, variables, inputCollection, loopsReceived);
    } else {
      return ParallelMultiInstanceProcessor.getSubProcessTriggersWhenActive(
          processInstance, processDefinition, element, variables, inputCollection, loopsReceived);
    }
  }

  private List<ProcessInstanceTrigger> getSubProcessTriggersWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      ScopedVars variables,
      JsonNode inputCollection,
      int i) {
    if (element.getLoopCharacteristics().isSequential()) {
      return SequentialMultiInstanceProcessor.getSubProcessTriggersWhenReady(
          processInstance, processDefinition, element, variables, inputCollection, i);
    } else {
      return ParallelMultiInstanceProcessor.getSubProcessTriggersWhenReady(
          processInstance, processDefinition, element, variables, inputCollection, i);
    }
  }

  private TriggerResult triggerWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables) {
    // Create ArrayNode as new OutputCollection and add it to the variables in the current scope
    ObjectMapper objectMapper = new ObjectMapper();

    String outputCollectionName = element.getLoopCharacteristics().getOutputCollection();
    if (outputCollectionName != null) {
      variables.put(outputCollectionName, objectMapper.createArrayNode());
    }
    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            element.getLoopCharacteristics().getInputCollection(), variables);
    if (inputCollection == null || inputCollection.isEmpty()) {
      return TriggerResult.builder()
          .newFlowNodeState(oldState.getFinishedLoopState())
          .processInstanceTriggers(
              TriggerHelper.getProcessInstanceTriggersForOutputFlows(
                  processInstance, processDefinition, element))
          .build();
    } else {
      List<ProcessInstanceTrigger> subProcessTriggers =
          getSubProcessTriggersWhenReady(
              processInstance, processDefinition, element, variables, inputCollection, 0);
      return TriggerResult.builder()
          .newFlowNodeState(oldState.getNextLoopState())
          .processInstanceTriggers(subProcessTriggers)
          .build();
    }
  }

  protected abstract TriggerResult triggerFlowElementWithoutLoop(
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      ScopedVars variables);

  protected TriggerResult finishActivity(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity<?> element,
      ActivityState newState,
      ScopedVars variables) {
    List<ProcessInstanceTrigger> triggers =
        TriggerHelper.getProcessInstanceTriggersForOutputFlows(
            processInstance, processDefinition, element);

    return TriggerResult.builder()
        .newFlowNodeState(newState)
        .processInstanceTriggers(triggers)
        .build();
  }
}
