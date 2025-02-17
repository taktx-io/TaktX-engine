package com.flomaestro.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.Activity;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.SequenceFlow;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.BoundaryEventInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceInfo;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.CatchEventStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import java.time.Clock;
import java.util.Set;
import lombok.NoArgsConstructor;
import org.apache.kafka.streams.state.KeyValueStore;

@NoArgsConstructor
public abstract class ActivityInstanceProcessor<
        E extends Activity, I extends ActivityInstance<E>, C extends ContinueFlowElementTriggerDTO>
    extends FlowNodeInstanceProcessor<E, I, C> {

  protected FeelExpressionHandler feelExpressionHandler;

  protected ActivityInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected final void processStartSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics) {

    if (flownodeInstance.isIteration()) {
      variables.put("loopCnt", new IntNode(flownodeInstance.getLoopCnt()));
      variables.put(
          flownodeInstance.getFlowNode().getLoopCharacteristics().getInputElement(),
          flownodeInstance.getInputElement());
    }

    processStartSpecificActivityInstance(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        flowElements,
        flownodeInstance,
        processInstance,
        inputFlowId,
        variables,
        processingStatistics);

    if (flownodeInstance.getState() == ActtivityStateEnum.WAITING) {
      E flowNode = flownodeInstance.getFlowNode();
      flowNode
          .getBoundaryEvents()
          .forEach(
              boundaryEvent -> {
                BoundaryEventInstance boundaryEventInstance =
                    new BoundaryEventInstance(
                        flownodeInstance.getParentInstance(),
                        boundaryEvent,
                        flowNodeInstances.nextElementInstanceId());
                boundaryEventInstance.setState(CatchEventStateEnum.INITIAL);

                boundaryEventInstance.setAttachedInstanceId(
                    flownodeInstance.getElementInstanceId());
                flownodeInstance.addBoundaryEventId(boundaryEventInstance.getElementInstanceId());
                directInstanceResult.addNewFlowNodeInstance(
                    processInstance, new FlowNodeInstanceInfo(boundaryEventInstance, null));
              });
    }

    handleFinishedIteration(flownodeInstance, variables);
  }

  @Override
  protected final void processContinueSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I flowNodeInstance,
      C trigger,
      VariableScope variables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {

    processContinueSpecificActivityInstance(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        processInstance,
        flowNodeInstance,
        trigger,
        variables,
        processingStatistics);

    if (flowNodeInstance.isCompleted()) {
      flowNodeInstance.getBoundaryEventIds().forEach(directInstanceResult::addTerminateInstance);
    }

    handleFinishedIteration(flowNodeInstance, variables);
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      VariableScope currentVariableScope,
      ProcessingStatistics processingStatistics,
      FlowElements flowElements) {

    instance.getBoundaryEventIds().forEach(directInstanceResult::addTerminateInstance);

    processTerminateSpecificActivityInstance(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        instance,
        processInstance,
        currentVariableScope,
        processingStatistics);
  }

  protected abstract void processStartSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics);

  protected abstract void processContinueSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      I externalTaskInstance,
      C trigger,
      VariableScope flowNodeInstanceVariables,
      ProcessingStatistics processingStatistics);

  protected abstract void processTerminateSpecificActivityInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      VariableScope variables,
      ProcessingStatistics processingStatistics);

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope variables) {
    if (flowNodeInstance.isIteration()) {
      return Set.of();
    }
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  private void handleFinishedIteration(I flownodeInstance, VariableScope variables) {
    if (flownodeInstance.getState() == ActtivityStateEnum.FINISHED
        && flownodeInstance.isIteration()) {
      Activity flowNode = flownodeInstance.getFlowNode();
      String outputElement = flowNode.getLoopCharacteristics().getOutputElement();
      JsonNode jsonNode = feelExpressionHandler.processFeelExpression(outputElement, variables);
      flownodeInstance.setOutputElement(jsonNode);
    }
    variables.remove("loopCnt");
    variables.remove(flownodeInstance.getFlowNode().getLoopCharacteristics().getInputElement());
  }
}
