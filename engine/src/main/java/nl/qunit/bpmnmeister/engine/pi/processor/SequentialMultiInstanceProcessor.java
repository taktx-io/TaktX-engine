package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.ActivityDTO;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowNodeDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTriggerIteration;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

@RequiredArgsConstructor
public class SequentialMultiInstanceProcessor extends StateProcessor<ActivityDTO, ActivityState> {

  final FeelExpressionHandler feelExpressionHandler;
  final ActivityProcessor activityProcessor;

  @Override
  public TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      FlowNodeDTO element,
      ScopedVars variables) {
    ActivityDTO activity = (ActivityDTO) element;
    if (trigger instanceof ContinueFlowElementTrigger continueFlowElementTrigger) {
      Optional<FlowNodeStateDTO> optFlowNodeState =
          processInstance
              .getFlowNodeStates()
              .get(continueFlowElementTrigger.getElementInstanceId());
      if (optFlowNodeState.isPresent()) {
        FlowNodeStateDTO flowNodeState = optFlowNodeState.get();
        if (flowNodeState instanceof ActivityState activityState) {
          TriggerResult triggerResult =
              activityProcessor.trigger(trigger, processInstance, definition, element, variables);
          return handleFinishedIterationResult(
              triggerResult,
              processInstance,
              definition,
              activity,
              variables,
              activityState.getParentElementInstanceId());
        }
      }
    } else if (trigger
        instanceof StartFlowElementTriggerIteration startFlowElementTriggerIteration) {
      TriggerResult iterationTriggerResult =
          activityProcessor.trigger(trigger, processInstance, definition, element, variables);
      return handleFinishedIterationResult(
          iterationTriggerResult,
          processInstance,
          definition,
          activity,
          variables,
          startFlowElementTriggerIteration.getParentElementInstance());
    } else if (trigger instanceof StartFlowElementTrigger flowElementTrigger) {
      return startNewMultiInstanceSequence(
          processInstance, definition, element, variables, flowElementTrigger, activity);
    }
    return TriggerResult.EMPTY;
  }

  private TriggerResult handleFinishedIterationResult(
      TriggerResult triggerResult,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      ActivityDTO activity,
      ScopedVars variables,
      UUID parentElementInstanceId) {
    FlowNodeStateDTO iterationTriggerState = triggerResult.getNewFlowNodeStates().get(0);
    if (iterationTriggerState.getState() == FlowNodeStateEnum.FINISHED) {

      // Store the output element in the output collection
      if (activity.getLoopCharacteristics().getOutputCollection() != null
          && activity.getLoopCharacteristics().getOutputElement() != null) {
        ArrayNode outputCollection =
            (ArrayNode) variables.get(activity.getLoopCharacteristics().getOutputCollection());
        JsonNode outputElementNode =
            feelExpressionHandler.processFeelExpression(
                activity.getLoopCharacteristics().getOutputElement(), variables);
        if (outputElementNode != null) {
          outputCollection.add(outputElementNode);
        }
        variables.put(activity.getLoopCharacteristics().getOutputCollection(), outputCollection);
      }

      JsonNode inputCollection =
          feelExpressionHandler.processFeelExpression(
              activity.getLoopCharacteristics().getInputCollection(), variables);
      Optional<FlowNodeStateDTO> optLoopFlowNodeState =
          processInstance.getFlowNodeStates().get(parentElementInstanceId);
      if (optLoopFlowNodeState.isPresent()) {
        MultiInstanceState oldState = (MultiInstanceState) optLoopFlowNodeState.get();

        MultiInstanceState nextLoopState = getNextLoopState(oldState);
        if (nextLoopState.getLoopCnt() < inputCollection.size()) {
          return TriggerResult.builder()
              .newFlowNodeStates(List.of(iterationTriggerState, nextLoopState))
              .processInstanceTriggers(
                  getChildElementTriggers(
                      processInstance, activity, inputCollection, nextLoopState))
              .build();
        } else {
          // all iterations finished
          MultiInstanceState finishedLoopState = getFinishedLoopState(oldState);
          List<ProcessInstanceTrigger> triggers =
              TriggerHelper.getProcessInstanceTriggersForOutputFlows(
                  processInstance, definition, finishedLoopState, activity);

          return TriggerResult.builder()
              .newFlowNodeStates(List.of(iterationTriggerState, finishedLoopState))
              .processInstanceTriggers(triggers)
              .build();
        }
      }
    }
    return triggerResult;
  }

  private TriggerResult startNewMultiInstanceSequence(
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      FlowNodeDTO element,
      ScopedVars variables,
      StartFlowElementTrigger flowElementTrigger,
      ActivityDTO activity) {
    MultiInstanceState state =
        getInitialState(flowElementTrigger.getElementId(), flowElementTrigger.getInputFlowId(), 0);
    // Create ArrayNode as new OutputCollection and add it to the variables in the current scope
    ObjectMapper objectMapper = new ObjectMapper();

    String outputCollectionName = activity.getLoopCharacteristics().getOutputCollection();
    if (outputCollectionName != null) {
      variables.put(outputCollectionName, objectMapper.createArrayNode());
    }
    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            activity.getLoopCharacteristics().getInputCollection(), variables);
    if (inputCollection == null || inputCollection.isEmpty()) {
      return TriggerResult.builder()
          .newFlowNodeStates(List.of(getFinishedLoopState(state)))
          .processInstanceTriggers(
              TriggerHelper.getProcessInstanceTriggersForOutputFlows(
                  processInstance, definition, state, element))
          .build();
    } else {
      List<ProcessInstanceTrigger> childElementTriggers =
          getChildElementTriggers(processInstance, activity, inputCollection, state);
      return TriggerResult.builder()
          .newFlowNodeStates(List.of(state))
          .processInstanceTriggers(childElementTriggers)
          .build();
    }
  }

  public static List<ProcessInstanceTrigger> getChildElementTriggers(
      ProcessInstance processInstance,
      ActivityDTO<?> element,
      JsonNode inputCollection,
      MultiInstanceState state) {

    List<ProcessInstanceTrigger> childElementTriggers = new ArrayList<>();

    JsonNode inputElement = inputCollection.get(state.getLoopCnt());
    VariablesDTO iterationVars =
        VariablesDTO.of(
            "loopCnt",
            new IntNode(state.getLoopCnt()),
            element.getLoopCharacteristics().getInputElement(),
            inputElement);
    childElementTriggers.add(
        new StartFlowElementTriggerIteration(
            processInstance.getProcessInstanceKey(),
            state.getElementInstanceId(),
            UUID.randomUUID(),
            element.getId(),
            Constants.NONE,
            iterationVars));

    return childElementTriggers;
  }

  private MultiInstanceState getNextLoopState(MultiInstanceState oldState) {
    return oldState.toBuilder()
        .state(FlowNodeStateEnum.WAITING)
        .loopCnt(oldState.getLoopCnt() + 1)
        .build();
  }

  private MultiInstanceState getFinishedLoopState(MultiInstanceState oldState) {
    return oldState.toBuilder()
        .passedCnt(oldState.getPassedCnt() + 1)
        .state(FlowNodeStateEnum.FINISHED)
        .build();
  }

  private MultiInstanceState getInitialState(String elementId, String inputFlowId, int i) {
    return new MultiInstanceState(
        UUID.randomUUID(), elementId, 0, FlowNodeStateEnum.WAITING, inputFlowId, 0);
  }
}
