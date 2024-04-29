package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.MultiInstanceState;

public abstract class MultiInstanceProcessor
    extends ActivityProcessor<Activity, MultiInstanceState> {

  @Inject FeelExpressionHandler feelExpressionHandler;

  @Override
  public TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {

    return switch (oldState.getState()) {
      case READY -> triggerWhenReady(processInstance, definition, element, oldState, variables);
      case ACTIVE -> triggerWhenActive(processInstance, definition, element, oldState, variables);
      default -> throw new IllegalStateException("Unknown state: " + oldState.getState());
    };
  }

  private TriggerResult triggerWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {
    // Create ArrayNode as new OutputCollection and add it to the variables
    ObjectMapper objectMapper = new ObjectMapper();
    ArrayNode outputCollection = objectMapper.createArrayNode();

    Variables returnVariables =
        new Variables(Map.of())
            .put(element.getLoopCharacteristics().getOutputCollection(), outputCollection);
    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            element.getLoopCharacteristics().getInputCollection(), variables);
    if (inputCollection == null || inputCollection.isEmpty()) {
      return new TriggerResult(
          new MultiInstanceState(
              ActivityStateEnum.FINISHED,
              oldState.getElementInstanceId(),
              oldState.getLoopCnt(),
              oldState.getPassedCnt() + 1),
          element.getOutgoing(),
          Set.of(),
          Set.of(),
          Set.of(),
          ThrowingEvent.NOOP,
          returnVariables);
    } else {
      Set<ProcessInstanceTrigger> subProcessTriggers =
          getSubProcessTriggersWhenReady(
              processInstance, processDefinition, element, variables, inputCollection, 0);

      return new TriggerResult(
          new MultiInstanceState(
              ActivityStateEnum.ACTIVE,
              oldState.getElementInstanceId(),
              0,
              oldState.getPassedCnt()),
          Set.of(),
          Set.of(),
          subProcessTriggers,
          Set.of(),
          ThrowingEvent.NOOP,
          returnVariables);
    }
  }

  private TriggerResult triggerWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      MultiInstanceState oldState,
      Variables variables) {

    // Store the output element in the output collection
    ArrayNode outputCollection =
        (ArrayNode) variables.get(element.getLoopCharacteristics().getOutputCollection());

    JsonNode outputElementNode =
        feelExpressionHandler.processFeelExpression(
            element.getLoopCharacteristics().getOutputElement(), variables);
    if (outputElementNode != null) {
      outputCollection.add(outputElementNode);
    }

    Variables returnVariables =
        new Variables(Map.of())
            .put(element.getLoopCharacteristics().getOutputCollection(), outputCollection);

    int loopsReceived = oldState.getLoopCnt() + 1;

    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            element.getLoopCharacteristics().getInputCollection(), variables);
    if (loopsReceived < inputCollection.size()) {
      Set<ProcessInstanceTrigger> subProcessTriggers =
          getSubProcessTriggersWhenActive(
              processInstance,
              processDefinition,
              element,
              variables,
              inputCollection,
              loopsReceived);

      return new TriggerResult(
          new MultiInstanceState(
              ActivityStateEnum.ACTIVE,
              oldState.getElementInstanceId(),
              loopsReceived,
              oldState.getPassedCnt() + 1),
          Set.of(),
          Set.of(),
          subProcessTriggers,
          Set.of(),
          ThrowingEvent.NOOP,
          returnVariables);
    } else {
      MultiInstanceState newState =
          new MultiInstanceState(
              ActivityStateEnum.FINISHED,
              oldState.getElementInstanceId(),
              loopsReceived,
              oldState.getPassedCnt() + 1);
      return finishActivity(processInstance, element, newState, returnVariables);
    }
  }

  protected abstract Set<ProcessInstanceTrigger> getSubProcessTriggersWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt);

  protected abstract Set<ProcessInstanceTrigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt);

  @Override
  public MultiInstanceState initialState() {
    return new MultiInstanceState(ActivityStateEnum.READY, UUID.randomUUID(), 0, 0);
  }
}
