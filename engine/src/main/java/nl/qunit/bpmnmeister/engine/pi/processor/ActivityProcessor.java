package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

public abstract class ActivityProcessor<E extends Activity, S extends ActivityState>
    extends StateProcessor<E, S> {

  @Inject FeelExpressionHandler feelExpressionHandler;

  @Override
  protected final TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      Variables variables) {
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
    Set<ProcessInstanceTrigger> triggers =
        new HashSet<>(triggerResult.getNewProcessInstanceTriggers());
    if (elementActivated(oldState, newElementState)) {
      for (BoundaryEvent boundaryEvent : boundaryEvents) {
        triggers.add(
            new FlowElementTrigger(
                processInstance.getProcessInstanceKey(),
                boundaryEvent.getId(),
                Constants.NONE,
                processInstance.getVariables()));
      }
    } else if (elementFinished(oldState, newElementState)) {
      for (BoundaryEvent boundaryEvent : boundaryEvents) {
        triggers.add(
            new TerminateTrigger(processInstance.getProcessInstanceKey(), boundaryEvent.getId()));
      }
    }

    return triggerResult.toBuilder().newProcessInstanceTriggers(triggers).build();
  }

  private TriggerResult getTriggerResultMultiInstanceOrSingle(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      Variables variables) {
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
      Variables variables) {
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
      Variables variables) {

    // Store the output element in the output collection
    Variables returnVariables = new Variables(Map.of());
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
      returnVariables =
          returnVariables.put(
              element.getLoopCharacteristics().getOutputCollection(), outputCollection);
    }

    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            element.getLoopCharacteristics().getInputCollection(), variables);
    if (oldState.getLoopCnt() < inputCollection.size()) {
      Set<ProcessInstanceTrigger> subProcessTriggers =
          getSubProcessTriggersWhenActive(
              processInstance,
              processDefinition,
              element,
              variables,
              inputCollection,
              oldState.getLoopCnt());

      return TriggerResult.builder()
          .newFlowNodeState(oldState.getNextLoopState())
          .newProcessInstanceTriggers(subProcessTriggers)
          .variables(returnVariables)
          .build();
    } else {
      return finishActivity(
          processInstance, element, oldState.getFinishedLoopState(), returnVariables);
    }
  }

  private Set<ProcessInstanceTrigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity<?> element,
      Variables variables,
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

  private Set<ProcessInstanceTrigger> getSubProcessTriggersWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      Variables variables,
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
      Variables variables) {
    // Create ArrayNode as new OutputCollection and add it to the variables
    ObjectMapper objectMapper = new ObjectMapper();

    String outputCollectionName = element.getLoopCharacteristics().getOutputCollection();
    Variables returnVariables = new Variables(Map.of());
    if (outputCollectionName != null) {
      returnVariables = returnVariables.put(outputCollectionName, objectMapper.createArrayNode());
    }
    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            element.getLoopCharacteristics().getInputCollection(), variables);
    if (inputCollection == null || inputCollection.isEmpty()) {
      return TriggerResult.builder()
          .newFlowNodeState(oldState.getFinishedLoopState())
          .newActiveFlows(element.getOutgoing())
          .variables(returnVariables)
          .build();
    } else {
      Set<ProcessInstanceTrigger> subProcessTriggers =
          getSubProcessTriggersWhenReady(
              processInstance, processDefinition, element, variables, inputCollection, 0);
      return TriggerResult.builder()
          .newFlowNodeState(oldState.getNextLoopState())
          .newProcessInstanceTriggers(subProcessTriggers)
          .variables(returnVariables)
          .build();
    }
  }

  protected abstract TriggerResult triggerFlowElementWithoutLoop(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      Variables variables);

  protected TriggerResult finishActivity(
      ProcessInstance processInstance,
      Activity<?> element,
      ActivityState newState,
      Variables returnVariables) {
    if (!processInstance.getParentInstanceKey().equals(ProcessInstanceKey.NONE)) {
      return TriggerResult.builder()
          .newFlowNodeState(newState)
          .newProcessInstanceTriggers(
              Set.of(
                  new FlowElementTrigger(
                      processInstance.getParentInstanceKey(),
                      element.getParentId(),
                      Constants.NONE,
                      processInstance.getVariables())))
          .variables(returnVariables)
          .build();
    } else {
      return TriggerResult.builder()
          .newFlowNodeState(newState)
          .newActiveFlows(element.getOutgoing())
          .variables(returnVariables)
          .build();
    }
  }
}
