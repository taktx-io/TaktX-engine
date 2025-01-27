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
import com.flomaestro.engine.pi.model.FlowNodeInstanceVariables;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.FlowNodeInstancesVariables;
import com.flomaestro.engine.pi.model.MultiInstanceInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import java.time.Clock;
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
      FlowNodeInstanceVariables variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  @Override
  protected void processStartSpecificFlowNodeInstance(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      FlowNodeInstanceVariables flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics) {

    Activity activity = multiInstanceInstance.getFlowNode();

    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            activity.getLoopCharacteristics().getInputCollection(), flowNodeInstanceVariables);

    if (inputCollection == null || inputCollection.isEmpty()) {
      multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
    } else {
      FlowNodeInstancesVariables flowNodeInstancesVariables =
          flowNodeInstanceVariables.selectFlowNodeInstancesScope(
              multiInstanceInstance.getFlowNodeInstances().getFlowNodeInstancesId());

      ActivityInstance iterationInstance =
          prepareIterationInstances(
              activity, multiInstanceInstance, inputCollection, flowNodeInstancesVariables);
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
          flowNodeInstancesVariables,
          activity);
      handleCompleted(
          flowNodeInstanceStore, flowElements, multiInstanceInstance, flowNodeInstancesVariables);
    }
  }

  private void loopStartIterations(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      ProcessingStatistics processingStatistics,
      ActivityInstance<?> iterationInstance,
      FlowNodeInstancesVariables flowNodeInstancesVariables,
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
      FlowNodeInstancesVariables flowNodeInstancesVariables) {
    ActivityInstance<?> previousIterationInstance = null;
    ActivityInstance<?> firstInstance = null;
    for (int i = 0; i < inputCollection.size(); i++) {
      ActivityInstance<?> iterationInstance = activity.newActivityInstance(multiInstanceInstance);
      iterationInstance.setState(ActtivityStateEnum.INITIAL);
      iterationInstance.setIteration(true);

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
      FlowNodeInstanceVariables iterationVariables =
          flowNodeInstancesVariables.selectFlowNodeInstanceScope(
              iterationInstance.getElementInstanceId());
      iterationVariables.put("loopCnt", new IntNode(i));
      iterationVariables.put(activity.getLoopCharacteristics().getInputElement(), inputElement);
    }
    return firstInstance;
  }

  private ActivityInstance<?> startIteration(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      ActivityInstance<?> iterationInstance,
      FlowElements flowElements,
      ProcessInstance processInstance,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      FlowNodeInstancesVariables flowNodeInstancesVariables,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      ProcessingStatistics processingStatistics) {

    FlowNodeInstanceVariables flowNodeInstanceVariables =
        flowNodeInstancesVariables.selectFlowNodeInstanceScope(
            iterationInstance.getElementInstanceId());

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
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTriggerDTO trigger,
      FlowNodeInstanceVariables flowNodeInstanceVariables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {
    subProcessLevel++;

    FlowNodeInstancesVariables flowNodeInstancesVariables =
        flowNodeInstanceVariables.selectFlowNodeInstancesScope(
            multiInstanceInstance.getFlowNodeInstances().getFlowNodeInstancesId());

    FlowElements subFlowElements = new FlowElements();
    subFlowElements.setParentElements(flowElements);
    Activity activity = multiInstanceInstance.getFlowNode();
    subFlowElements.addFlowElement(activity);

    UUID instanceId = trigger.getElementInstanceIdPath().get(subProcessLevel);

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            multiInstanceInstance.getFlowNodeInstances(), flowNodeInstanceStore, flowElements);

    ActivityInstance<?> iterationInstance =
        (ActivityInstance<?>) storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(instanceId);

    while (iterationInstance != null) {
      FlowNodeInstanceVariables iterationVariables =
          flowNodeInstancesVariables.selectFlowNodeInstanceScope(
              iterationInstance.getElementInstanceId());
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
              flowNodeInstancesVariables,
              activity);
        }
      } else {
        iterationInstance = null;
      }
    }
    handleCompleted(
        flowNodeInstanceStore, flowElements, multiInstanceInstance, flowNodeInstancesVariables);
  }

  private ActivityInstance<?> getNextIterationInstance(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances,
      ActivityInstance<?> iterationInstance,
      FlowElements flowElements) {
    if (iterationInstance.getNextIterationId() != null) {
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              flowNodeInstances, flowNodeInstanceStore, flowElements);
      return (ActivityInstance<?>)
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              iterationInstance.getNextIterationId());
    } else {
      return null;
    }
  }

  private static void handleCompleted(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      FlowNodeInstancesVariables flowNodeInstancesVariables) {
    multiInstanceInstance.getFlowNodeInstances().determineImplicitCompletedState();
    if (multiInstanceInstance.getFlowNodeInstances().getState().isFinished()) {
      multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
      ArrayNode arrayNode = new ObjectMapper().createArrayNode();

      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              multiInstanceInstance.getFlowNodeInstances(), flowNodeInstanceStore, flowElements);

      storedFlowNodeInstancesWrapper.getAllInstances().values().stream()
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
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      MultiInstanceInstance instance,
      ProcessInstance processInstance,
      FlowNodeInstanceVariables variables,
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
