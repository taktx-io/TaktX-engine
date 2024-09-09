package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import nl.qunit.bpmnmeister.pd.model.Activity2;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
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
      Activity2 activity,
      MultiInstanceInstance multiInstanceInstance,
      Variables2 variables) {

    ObjectMapper objectMapper = new ObjectMapper();

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
        ActivityInstance activityInstance = null;

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
                  multiInstanceInstance.getLoopCnt(),
                  instanceResult,
                  inputCollection);
          multiInstanceInstance.increaseLoopCnt();
        }
      } else {
        for (int i = 0; i < inputCollection.size(); i++) {
          startIteration(
              flowElements,
              activity,
              multiInstanceInstance,
              variables,
              i,
              instanceResult,
              inputCollection);
          multiInstanceInstance.increaseLoopCnt();
        }
      }

      if (multiInstanceInstance.getLoopCnt() >= (inputCollection.size())
          && multiInstanceInstance.allCompleted()) {
        multiInstanceInstance.setState(FlowNodeStateEnum.FINISHED);
      }
      return instanceResult;
    }
  }

  private ActivityInstance startIteration(
      FlowElements2 flowElements,
      Activity2 activity,
      MultiInstanceInstance multiInstanceInstance,
      Variables2 variables,
      int i,
      InstanceResult instanceResult,
      JsonNode inputCollection) {
    ActivityInstance instance = activity.newActivityInstance(multiInstanceInstance);
    instance.setLoopCnt(i);
    multiInstanceInstance.setLoopCnt(i);
    multiInstanceInstance.addInstance(instance);
    InstanceResult toMerge =
        processor.processStart(flowElements, activity, instance, variables, true);
    instanceResult.merge(toMerge);

    JsonNode inputElement = inputCollection.get(i);
    Variables2 iterationVars =
        Variables2.of(
            "loopCnt",
            new IntNode(i),
            activity.getLoopCharacteristics().getInputElement(),
            inputElement);
    iterationVars.merge(variables);

    // Store the output element in the output collection
    if (activity.getLoopCharacteristics().getOutputCollection() != null
        && activity.getLoopCharacteristics().getOutputElement() != null) {
      ArrayNode outputCollection =
          (ArrayNode) variables.get(activity.getLoopCharacteristics().getOutputCollection());
      JsonNode outputElementNode =
          feelExpressionHandler.processFeelExpression(
              activity.getLoopCharacteristics().getOutputElement(), iterationVars);
      if (outputElementNode != null) {
        outputCollection.add(outputElementNode);
      }
      variables.put(activity.getLoopCharacteristics().getOutputCollection(), outputCollection);
    }
    return instance;
  }

  @Override
  protected InstanceResult processContinueSpecificFlowNodeInstance(
      int subProcessLevel,
      FlowElements2 flowElements,
      Activity2 flowNode,
      MultiInstanceInstance flowNodeInstance,
      ContinueFlowElementTrigger2 trigger,
      Variables2 variables) {
    return null;
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(
      Activity2 flowNode, MultiInstanceInstance instance) {
    return null;
  }
}
