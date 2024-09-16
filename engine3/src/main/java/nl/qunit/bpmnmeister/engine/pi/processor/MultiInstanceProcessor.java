package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.Activity2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.MultiInstanceInstance;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;

public class MultiInstanceProcessor
    extends FLowNodeInstanceProcessor<
        Activity2, MultiInstanceInstance, ContinueFlowElementTrigger2> {
  private final FeelExpressionHandler feelExpressionHandler;
  private final ActivityInstanceProcessor<?, ?, ?> processor;

  public MultiInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler, ActivityInstanceProcessor<?, ?, ?> processor) {
    super(processor.getIoMappingProcessor());
    this.processor = processor;
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements2 flowElements,
      MultiInstanceInstance multiInstanceInstance,
      Variables2 variables) {

    ObjectMapper objectMapper = new ObjectMapper();
    Activity2 activity = multiInstanceInstance.getFlowNode();
    String outputCollectionName = activity.getLoopCharacteristics().getOutputCollection();
    if (outputCollectionName != null) {
      variables.put(outputCollectionName, objectMapper.createArrayNode());
    }
    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            activity.getLoopCharacteristics().getInputCollection(), variables);
    if (inputCollection == null || inputCollection.isEmpty()) {
      multiInstanceInstance.setState(FlowNodeStateEnum.FINISHED);
      return InstanceResult.empty();
    } else {
      InstanceResult instanceResult = InstanceResult.empty();
      multiInstanceInstance.setState(FlowNodeStateEnum.WAITING);

      if (activity.getLoopCharacteristics().isSequential()) {
        ActivityInstance<?> activityInstance = null;

        while ((activityInstance == null
                || activityInstance.getState() == FlowNodeStateEnum.FINISHED)
            && multiInstanceInstance.getLoopCnt() < inputCollection.size()) {
          // Keep starting instances whhen they are finished immediately
          activityInstance =
              startIteration(
                  flowElements,
                  activity,
                  multiInstanceInstance,
                  variables,
                  instanceResult,
                  inputCollection);
        }
      } else {
        for (int i = 0; i < inputCollection.size(); i++) {
          startIteration(
              flowElements,
              activity,
              multiInstanceInstance,
              variables,
              instanceResult,
              inputCollection);
        }
      }

      if (multiInstanceInstance.getLoopCnt() >= (inputCollection.size())
          && multiInstanceInstance.getFlowNodeStates().allCompleted()) {
        multiInstanceInstance.setState(FlowNodeStateEnum.FINISHED);
      }
      return instanceResult;
    }
  }

  private ActivityInstance<?> startIteration(
      FlowElements2 flowElements,
      Activity2 activity,
      MultiInstanceInstance multiInstanceInstance,
      Variables2 variables,
      InstanceResult instanceResult,
      JsonNode inputCollection) {

    ActivityInstance<?> instance = activity.newActivityInstance(multiInstanceInstance);
    instance.setLoopCnt(multiInstanceInstance.getLoopCnt());
    multiInstanceInstance.getFlowNodeStates().putInstance(instance);

    JsonNode inputElement = inputCollection.get(multiInstanceInstance.getLoopCnt());
    Variables2 iterationVars =
        Variables2.of(
            "loopCnt",
            new IntNode(multiInstanceInstance.getLoopCnt()),
            activity.getLoopCharacteristics().getInputElement(),
            inputElement);
    variables.merge(iterationVars);

    InstanceResult toMerge = processor.processStart(flowElements, instance, iterationVars, true);
    instanceResult.merge(toMerge);

    storeOutputCollectionIfCompleted(activity, variables, instance, iterationVars);
    multiInstanceInstance.increaseLoopCnt();
    return instance;
  }

  private void storeOutputCollectionIfCompleted(
      Activity2 activity,
      Variables2 variables,
      FLowNodeInstance<?> instance,
      Variables2 iterationVars) {
    if (instance.isCompleted()
        && activity.getLoopCharacteristics().getOutputCollection() != null
        && activity.getLoopCharacteristics().getOutputElement() != null) {
      ArrayNode outputCollection =
          (ArrayNode) variables.get(activity.getLoopCharacteristics().getOutputCollection());
      JsonNode outputElementNode =
          feelExpressionHandler.processFeelExpression(
              activity.getLoopCharacteristics().getOutputElement(), iterationVars);
      if (outputElementNode != null) {
        outputCollection.add(outputElementNode);
      }
    }
  }

  @Override
  protected InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 variables) {
    subProcessLevel++;

    UUID subElementId = trigger.getElementInstanceIdPath().get(subProcessLevel);
    FLowNodeInstance<?> iterationInstance =
        multiInstanceInstance.getFlowNodeStates().get(subElementId);
    FlowElements2 subFlowElements = new FlowElements2();
    Activity2 activity = multiInstanceInstance.getFlowNode();
    subFlowElements.addFlowElement(activity);

    if (iterationInstance != null) {
      InstanceResult instanceResult =
          processor.processContinue(
              subProcessLevel, subFlowElements, iterationInstance, trigger, variables, true);

      storeOutputCollectionIfCompleted(activity, variables, iterationInstance, variables);

      JsonNode inputCollection =
          feelExpressionHandler.processFeelExpression(
              activity.getLoopCharacteristics().getInputCollection(), variables);
      while (iterationInstance.isCompleted()
          && multiInstanceInstance.getLoopCnt() < inputCollection.size()) {
        iterationInstance =
            startIteration(
                flowElements,
                activity,
                multiInstanceInstance,
                variables,
                instanceResult,
                inputCollection);
      }

      multiInstanceInstance.getFlowNodeStates().determineImplicitCompletedState();

      if (multiInstanceInstance.getFlowNodeStates().getState().isFinished()) {
        multiInstanceInstance.setState(FlowNodeStateEnum.FINISHED);
      }
      return instanceResult;
    }
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(
      Activity2 flowNode, MultiInstanceInstance instance) {
    InstanceResult instanceResult = InstanceResult.empty();
    instance
        .getFlowNodeStates()
        .getFlowNodeInstances()
        .values()
        .forEach(
            iteration -> instanceResult.merge(processor.processTerminate(flowNode, iteration)));
    return instanceResult;
  }
}
