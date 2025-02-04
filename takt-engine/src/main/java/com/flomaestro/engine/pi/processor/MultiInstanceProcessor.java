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
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.StoredFlowNodeInstancesWrapper;
import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.MultiInstanceInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.streams.state.KeyValueStore;

public class MultiInstanceProcessor
    extends FlowNodeInstanceProcessor<
        Activity, MultiInstanceInstance, ContinueFlowElementTriggerDTO> {

  private final FeelExpressionHandler feelExpressionHandler;
  private final ActivityInstanceProcessor<?, ?, ?> processor;

  public MultiInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      ActivityInstanceProcessor<?, ?, ?> activityInstanceProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(activityInstanceProcessor.getIoMappingProcessor(), processInstanceMapper, clock);
    this.processor = activityInstanceProcessor;
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      MultiInstanceInstance flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  @Override
  protected void processStartSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances, InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {

    Activity activity = multiInstanceInstance.getFlowNode();

    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            activity.getLoopCharacteristics().getInputCollection(), flowNodeInstanceVariables);

    if (inputCollection == null || inputCollection.isEmpty()) {
      multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
    } else {
      ActivityInstance iterationInstance =
          prepareIterationInstances(
              activity, multiInstanceInstance, inputCollection, flowNodeInstanceVariables, multiInstanceInstance.getFlowNodeInstances());
      loopStartIterations(
          flowNodeInstanceStore,
          instanceResult,
          directInstanceResult,
          flowElements,
          multiInstanceInstance,
          processInstance,
          inputFlowId,
          processingStatistics,
          iterationInstance,
          flowNodeInstanceVariables,
          activity);
      handleCompleted(
          processInstance.getProcessInstanceKey(),
          flowNodeInstanceStore, flowElements, multiInstanceInstance, flowNodeInstanceVariables);
    }
  }

  private void loopStartIterations(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      ProcessingStatistics processingStatistics,
      ActivityInstance<?> iterationInstance,
      VariableScope flowNodeInstancesVariables,
      Activity activity) {
    while (iterationInstance != null) {
      startIteration(
          flowNodeInstanceStore,
          iterationInstance,
          flowElements,
          processInstance,
          multiInstanceInstance,
          inputFlowId,
          flowNodeInstancesVariables,
          instanceResult,
          directInstanceResult,
          processingStatistics);
      if (iterationInstance.isAwaiting()) {
        multiInstanceInstance.setState(ActtivityStateEnum.WAITING);
      }
      if (activity.getLoopCharacteristics().isSequential()) {
        if (iterationInstance.getState() == ActtivityStateEnum.FINISHED) {
          iterationInstance =
              getNextIterationInstance(
                  flowNodeInstanceStore,
                  processInstance.getProcessInstanceKey(),
                  multiInstanceInstance.getFlowNodeInstances(),
                  iterationInstance,
                  flowElements);
        } else {
          iterationInstance = null;
        }
      } else {
        iterationInstance =
            getNextIterationInstance(
                flowNodeInstanceStore,
                processInstance.getProcessInstanceKey(),
                multiInstanceInstance.getFlowNodeInstances(),
                iterationInstance,
                flowElements);
      }
    }
  }

  private ActivityInstance<?> prepareIterationInstances(
      Activity activity,
      MultiInstanceInstance multiInstanceInstance,
      JsonNode inputCollection,
      VariableScope flowNodeInstancesVariables,
      FlowNodeInstances flowNodeInstances) {
    ActivityInstance<?> previousIterationInstance = null;
    ActivityInstance<?> firstInstance = null;
    for (int i = 0; i < inputCollection.size(); i++) {
      ActivityInstance<?> iterationInstance = activity.newActivityInstance(multiInstanceInstance, flowNodeInstances.nextElementInstanceId());
      iterationInstance.setState(ActtivityStateEnum.INITIAL);
      iterationInstance.setIteration(true);
      iterationInstance.setLoopCnt(i);

      if (firstInstance == null) {
        firstInstance = iterationInstance;
      }

      if (previousIterationInstance != null) {
        previousIterationInstance.setNextIterationId(iterationInstance.getElementInstanceId());
      }

      previousIterationInstance = iterationInstance;
      iterationInstance.setLoopCnt(i);
      multiInstanceInstance.getFlowNodeInstances().putInstance(iterationInstance);
      JsonNode inputElement = inputCollection.get(i);
      iterationInstance.setInputElement(inputElement);
      VariableScope iterationVariables =
          flowNodeInstancesVariables.selectFlowNodeInstancesScope(
              iterationInstance.getElementInstanceId());
      iterationVariables.put("loopCnt", new IntNode(i));
      iterationVariables.put(activity.getLoopCharacteristics().getInputElement(), inputElement);
    }
    return firstInstance;
  }

  private ActivityInstance<?> startIteration(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      ActivityInstance<?> iterationInstance,
      FlowElements flowElements,
      ProcessInstance processInstance,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      VariableScope flowNodeInstancesVariables,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      ProcessingStatistics processingStatistics) {

    VariableScope flowNodeInstanceVariables =
        flowNodeInstancesVariables.selectFlowNodeInstancesScope(
            iterationInstance.getElementInstanceId());
    flowNodeInstanceVariables.put("loopCnt", new IntNode(iterationInstance.getLoopCnt()));
    flowNodeInstanceVariables.put(
        multiInstanceInstance.getFlowNode().getLoopCharacteristics().getInputElement(),
        iterationInstance.getInputElement());

    processor.processStart(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowElements,
        iterationInstance,
        processInstance,
        inputFlowId,
        flowNodeInstanceVariables,
        multiInstanceInstance.getFlowNodeInstances(),
        processingStatistics);

    return iterationInstance;
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {
    subProcessLevel++;

    FlowElements subFlowElements = new FlowElements();
    Activity activity = multiInstanceInstance.getFlowNode();
    subFlowElements.addFlowElement(activity);

    long instanceId = trigger.getElementInstanceIdPath().get(subProcessLevel);

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstance.getProcessInstanceKey(),
            multiInstanceInstance.getFlowNodeInstances(),
            flowNodeInstanceStore,
            flowElements);

    ActivityInstance<?> iterationInstance =
        (ActivityInstance<?>) storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(instanceId);

    VariableScope iterationVariables = flowNodeInstanceVariables.selectFlowNodeInstancesScope(
        iterationInstance.getElementInstanceId());

    while (iterationInstance != null) {
      processor.processContinue(
          flowNodeInstanceStore,
          instanceResult,
          directInstanceResult,
          subProcessLevel,
          subFlowElements,
          processInstance,
          iterationInstance,
          trigger,
          iterationVariables,
          multiInstanceInstance.getFlowNodeInstances(),
          processingStatistics);
      if (iterationInstance.getState() == ActtivityStateEnum.FINISHED) {

        iterationInstance =
            getNextIterationInstance(
                flowNodeInstanceStore,
                processInstance.getProcessInstanceKey(),
                multiInstanceInstance.getFlowNodeInstances(),
                iterationInstance,
                flowElements);
        if (activity.getLoopCharacteristics().isSequential()) {
          loopStartIterations(
              flowNodeInstanceStore,
              instanceResult,
              directInstanceResult,
              flowElements,
              multiInstanceInstance,
              processInstance,
              trigger.getInputFlowId(),
              processingStatistics,
              iterationInstance,
              flowNodeInstanceVariables,
              activity);
        }
      } else {
        iterationInstance = null;
      }
    }
    handleCompleted(
        processInstance.getProcessInstanceKey(),
        flowNodeInstanceStore,
        flowElements,
        multiInstanceInstance,
        flowNodeInstanceVariables);
  }

  private ActivityInstance<?> getNextIterationInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      UUID processInstanceKey,
      FlowNodeInstances flowNodeInstances,
      ActivityInstance<?> iterationInstance,
      FlowElements flowElements) {
    if (iterationInstance.getNextIterationId() >= 0) {
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceKey,
              flowNodeInstances, flowNodeInstanceStore, flowElements);
      return (ActivityInstance<?>)
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              iterationInstance.getNextIterationId());
    } else {
      return null;
    }
  }

  private static void handleCompleted(
      UUID processInstanceKey,
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      VariableScope flowNodeInstancesVariables) {
    multiInstanceInstance.getFlowNodeInstances().determineImplicitCompletedState();
    if (multiInstanceInstance.getFlowNodeInstances().getState().isFinished()) {
      multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
      ArrayNode arrayNode = new ObjectMapper().createArrayNode();

      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceKey,
              multiInstanceInstance.getFlowNodeInstances(),
              flowNodeInstanceStore,
              flowElements);

      Map<Long, FlowNodeInstance<?>> allInstances = storedFlowNodeInstancesWrapper.getAllInstances();
      allInstances.values().stream()
          .filter(flowNodeInstance -> flowNodeInstance instanceof ActivityInstance<?>)
          .map(flowNodeInstance -> (ActivityInstance<?>) flowNodeInstance)
          .map(iterationInstance -> iterationInstance.getOutputElement())
          .forEach(outputElement -> arrayNode.add(outputElement));
      String outputCollection =
          multiInstanceInstance.getFlowNode().getLoopCharacteristics().getOutputCollection();
      if (outputCollection != null) {
        flowNodeInstancesVariables.put(outputCollection, arrayNode);
      }
    }
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      MultiInstanceInstance instance,
      ProcessInstance processInstance,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {
    FlowNodeInstances flowNodeInstances = instance.getFlowNodeInstances();
    flowNodeInstances
        .getInstances()
        .values()
        .forEach(
            iteration ->
                processor.processTerminate(
                    flowNodeInstanceStore,
                    instanceResult,
                    directInstanceResult,
                    iteration,
                    processInstance,
                    variables,
                    flowNodeInstances,
                    processingStatistics));
  }
}
