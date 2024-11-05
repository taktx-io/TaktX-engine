package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.VariablesMapper;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.MultiInstanceInstance;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;

public class MultiInstanceProcessor
    extends FLowNodeInstanceProcessor<Activity, MultiInstanceInstance, ContinueFlowElementTrigger> {
  private final FeelExpressionHandler feelExpressionHandler;
  private final ObjectMapper objectMapper;
  private final ActivityInstanceProcessor<?, ?, ?> processor;

  public MultiInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      ActivityInstanceProcessor<?, ?, ?> processor,
      VariablesMapper variablesMapper,
      ObjectMapper objectMapper) {
    super(processor.getIoMappingProcessor(), variablesMapper);
    this.processor = processor;
    this.feelExpressionHandler = feelExpressionHandler;
    this.objectMapper = objectMapper;
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      MultiInstanceInstance flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      Variables variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  @Override
  protected InstanceResult processStartSpecificFlowNodeInstance(
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      Variables variables) {

    Activity activity = multiInstanceInstance.getFlowNode();
    String outputCollectionName = activity.getLoopCharacteristics().getOutputCollection();
    if (outputCollectionName != null) {
      variables.put(outputCollectionName, objectMapper.createArrayNode());
    }
    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            activity.getLoopCharacteristics().getInputCollection(), variables);
    if (inputCollection == null || inputCollection.isEmpty()) {
      multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
      return InstanceResult.empty();
    } else {
      InstanceResult instanceResult = InstanceResult.empty();
      multiInstanceInstance.setState(ActtivityStateEnum.WAITING);

      if (activity.getLoopCharacteristics().isSequential()) {
        ActivityInstance<?> activityInstance = null;

        while ((activityInstance == null
                || activityInstance.getState() == ActtivityStateEnum.FINISHED)
            && multiInstanceInstance.getLoopCnt() < inputCollection.size()) {
          // Keep starting instances whhen they are finished immediately
          activityInstance =
              startIteration(
                  flowElements,
                  activity,
                  multiInstanceInstance,
                  inputFlowId,
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
              inputFlowId,
              variables,
              instanceResult,
              inputCollection);
        }
      }

      if (multiInstanceInstance.getLoopCnt() >= (inputCollection.size())
          && multiInstanceInstance.getFlowNodeInstances().allCompleted()) {
        multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
      }
      return instanceResult;
    }
  }

  private ActivityInstance<?> startIteration(
      FlowElements flowElements,
      Activity activity,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      Variables variables,
      InstanceResult instanceResult,
      JsonNode inputCollection) {

    ActivityInstance<?> instance = activity.newActivityInstance(multiInstanceInstance);
    instance.setLoopCnt(multiInstanceInstance.getLoopCnt());
    multiInstanceInstance.getFlowNodeInstances().putInstance(instance);

    JsonNode inputElement = inputCollection.get(multiInstanceInstance.getLoopCnt());
    Variables iterationVars =
        Variables.of(
            "loopCnt",
            new IntNode(multiInstanceInstance.getLoopCnt()),
            activity.getLoopCharacteristics().getInputElement(),
            inputElement);
    variables.merge(iterationVars);

    InstanceResult toMerge =
        processor.processStart(
            flowElements,
            instance,
            inputFlowId,
            iterationVars,
            true,
            multiInstanceInstance.getFlowNodeInstances());
    instanceResult.merge(toMerge);

    storeOutputCollectionIfCompleted(activity, variables, instance, iterationVars);
    multiInstanceInstance.increaseLoopCnt();
    return instance;
  }

  private void storeOutputCollectionIfCompleted(
      Activity activity,
      Variables variables,
      FLowNodeInstance<?> instance,
      Variables iterationVars) {
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
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTrigger trigger,
      Variables variables,
      FlowNodeInstances flowNodeInstances) {
    subProcessLevel++;

    UUID subElementId = trigger.getElementInstanceIdPath().get(subProcessLevel);
    FLowNodeInstance<?> iterationInstance =
        multiInstanceInstance.getFlowNodeInstances().getInstanceWithInstanceId(subElementId);
    FlowElements subFlowElements = new FlowElements();
    subFlowElements.setParentElements(flowElements);

    Activity activity = multiInstanceInstance.getFlowNode();
    subFlowElements.addFlowElement(activity);

    if (iterationInstance != null) {
      InstanceResult instanceResult =
          processor.processContinue(
              subProcessLevel,
              subFlowElements,
              iterationInstance,
              trigger,
              variables,
              true,
              flowNodeInstances);

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
                Constants.NONE,
                variables,
                instanceResult,
                inputCollection);
      }

      multiInstanceInstance.getFlowNodeInstances().determineImplicitCompletedState();

      if (multiInstanceInstance.getFlowNodeInstances().getState().isFinished()) {
        multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
      }
      return instanceResult;
    }
    return InstanceResult.empty();
  }

  @Override
  protected InstanceResult processTerminateSpecificFlowNodeInstance(
      MultiInstanceInstance instance, Variables variables) {
    InstanceResult instanceResult = InstanceResult.empty();
    instance
        .getFlowNodeInstances()
        .getInstances()
        .values()
        .forEach(
            iteration -> instanceResult.merge(processor.processTerminate(iteration, variables)));
    return instanceResult;
  }
}
