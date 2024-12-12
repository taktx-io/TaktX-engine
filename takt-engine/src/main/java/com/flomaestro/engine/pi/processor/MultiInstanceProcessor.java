package com.flomaestro.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.Activity;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.SequenceFlow;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.VariablesMapper;
import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.MultiInstanceInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import java.util.Set;
import java.util.UUID;

public class MultiInstanceProcessor
    extends FLowNodeInstanceProcessor<
        Activity, MultiInstanceInstance, ContinueFlowElementTriggerDTO> {
  private final FeelExpressionHandler feelExpressionHandler;
  private final ObjectMapper objectMapper;
  private final ActivityInstanceProcessor<?, ?, ?> processor;

  public MultiInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      ActivityInstanceProcessor<?, ?, ?> processor,
      VariablesMapper variablesMapper,
      ProcessInstanceMapper processInstanceMapper,
      ObjectMapper objectMapper) {
    super(processor.getIoMappingProcessor(), processInstanceMapper, variablesMapper);
    this.processor = processor;
    this.feelExpressionHandler = feelExpressionHandler;
    this.objectMapper = objectMapper;
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      MultiInstanceInstance flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      Variables variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  @Override
  protected void processStartSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      ProcessInstance processInstance,
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
    } else {
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
                  processInstance,
                  multiInstanceInstance,
                  inputFlowId,
                  variables,
                  instanceResult,
                  directInstanceResult,
                  inputCollection);
        }
      } else {
        for (int i = 0; i < inputCollection.size(); i++) {
          startIteration(
              flowElements,
              activity,
              processInstance,
              multiInstanceInstance,
              inputFlowId,
              variables,
              instanceResult,
              directInstanceResult,
              inputCollection);
        }
      }

      if (multiInstanceInstance.getLoopCnt() >= (inputCollection.size())
          && multiInstanceInstance.getFlowNodeInstances().allCompleted()) {
        multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
      }
    }
  }

  private ActivityInstance<?> startIteration(
      FlowElements flowElements,
      Activity activity,
      ProcessInstance processInstance,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      Variables variables,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
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

    processor.processStart(
        instanceResult,
        directInstanceResult,
        flowElements,
        instance,
        processInstance,
        inputFlowId,
        iterationVars,
        true,
        multiInstanceInstance.getFlowNodeInstances());

    storeOutputCollectionIfCompleted(activity, variables, instance, iterationVars);
    multiInstanceInstance.increaseLoopCnt();
    return instance;
  }

  private void storeOutputCollectionIfCompleted(
      Activity activity,
      Variables variables,
      FlowNodeInstance<?> instance,
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
  protected void processContinueSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTriggerDTO trigger,
      Variables variables,
      FlowNodeInstances flowNodeInstances) {
    subProcessLevel++;

    UUID subElementId = trigger.getElementInstanceIdPath().get(subProcessLevel);
    FlowNodeInstance<?> iterationInstance =
        multiInstanceInstance.getFlowNodeInstances().getInstanceWithInstanceId(subElementId);
    FlowElements subFlowElements = new FlowElements();
    subFlowElements.setParentElements(flowElements);

    Activity activity = multiInstanceInstance.getFlowNode();
    subFlowElements.addFlowElement(activity);

    if (iterationInstance != null) {
      processor.processContinue(
          instanceResult,
          directInstanceResult,
          subProcessLevel,
          subFlowElements,
          processInstance,
          iterationInstance,
          trigger,
          variables,
          true,
          multiInstanceInstance.getFlowNodeInstances());

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
                processInstance,
                multiInstanceInstance,
                Constants.NONE,
                variables,
                instanceResult,
                directInstanceResult,
                inputCollection);
      }

      multiInstanceInstance.getFlowNodeInstances().determineImplicitCompletedState();

      if (multiInstanceInstance.getFlowNodeInstances().getState().isFinished()) {
        multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
      }
    }
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      MultiInstanceInstance instance,
      ProcessInstance processInstance,
      Variables variables) {
    FlowNodeInstances flowNodeInstances = instance.getFlowNodeInstances();
    flowNodeInstances
        .getInstances()
        .values()
        .forEach(
            iteration ->
                processor.processTerminate(
                    instanceResult,
                    directInstanceResult,
                    iteration,
                    processInstance,
                    variables,
                    flowNodeInstances));
  }
}
