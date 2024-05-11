package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityState;

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
    if (element.getLoopCharacteristics().equals(LoopCharacteristics.NONE)) {
      return triggerFlowElementWithoutLoop(
          trigger, processInstance, definition, element, oldState, variables);
    } else {
      return triggerFlowElementWithLoop(
          trigger, processInstance, definition, element, oldState, variables);
    }
  }

  private TriggerResult triggerFlowElementWithLoop(
      FlowElementTrigger trigger,
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

      return new TriggerResult(
          oldState.getNextLoopState(),
          Set.of(),
          Set.of(),
          subProcessTriggers,
          Set.of(),
          ThrowingEvent.NOOP,
          Set.of(),
          returnVariables);
    } else {
      return finishActivity(
          processInstance, element, oldState.getFinishedLoopState(), returnVariables);
    }
  }

  private Set<ProcessInstanceTrigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopsReceived) {
    if (element.getLoopCharacteristics().getIsSequential()) {
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
    if (element.getLoopCharacteristics().getIsSequential()) {
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
      return new TriggerResult(
          oldState.getFinishedLoopState(),
          element.getOutgoing(),
          Set.of(),
          Set.of(),
          Set.of(),
          ThrowingEvent.NOOP,
          Set.of(),
          returnVariables);
    } else {
      Set<ProcessInstanceTrigger> subProcessTriggers =
          getSubProcessTriggersWhenReady(
              processInstance, processDefinition, element, variables, inputCollection, 0);

      return new TriggerResult(
          oldState.getNextLoopState(),
          Set.of(),
          Set.of(),
          subProcessTriggers,
          Set.of(),
          ThrowingEvent.NOOP,
          Set.of(),
          returnVariables);
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
      Activity element,
      ActivityState newState,
      Variables returnVariables) {
    if (!processInstance.getParentInstanceKey().equals(ProcessInstanceKey.NONE)) {
      return new TriggerResult(
          newState,
          Set.of(),
          Set.of(),
          Set.of(
              new FlowElementTrigger(
                  processInstance.getParentInstanceKey(),
                  element.getParentId(),
                  Constants.NONE,
                  processInstance.getVariables())),
          Set.of(),
          ThrowingEvent.NOOP,
          Set.of(),
          returnVariables);
    } else {
      return new TriggerResult(
          newState,
          element.getOutgoing(),
          Set.of(),
          Set.of(),
          Set.of(),
          ThrowingEvent.NOOP,
          Set.of(),
          returnVariables);
    }
  }
}
