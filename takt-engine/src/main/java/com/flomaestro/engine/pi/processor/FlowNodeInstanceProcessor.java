package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.FlowNode;
import com.flomaestro.engine.pd.model.SequenceFlow;
import com.flomaestro.engine.pd.model.WithIoMapping;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.InstanceUpdate;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstanceInfo;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.kafka.streams.state.KeyValueStore;

@Getter
@Setter
@NoArgsConstructor
public abstract class FlowNodeInstanceProcessor<
    E extends FlowNode, I extends FlowNodeInstance<?>, C extends ContinueFlowElementTriggerDTO> {
  protected IoMappingProcessor ioMappingProcessor;
  protected Clock clock;
  protected ProcessInstanceMapper processInstanceMapper;

  protected FlowNodeInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    this.ioMappingProcessor = ioMappingProcessor;
    this.processInstanceMapper = processInstanceMapper;
    this.clock = clock;
  }

  public void processStart(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      FlowNodeInstance<?> flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {

    if (!flownodeInstance.stateAllowsStart()) {
      return;
    }

    flownodeInstance.setStartedState();

    processingStatistics.increaseFlowNodesStarted();

    long now = clock.instant().toEpochMilli();

    E flowNode = (E) flownodeInstance.getFlowNode();

    addInputVariablesToScope(flowNode, flowNodeInstanceVariables);

    this.processStartSpecificFlowNodeInstance(
        flowNodeInstanceStore,
        flowNodeInstances,
        instanceResult,
        directInstanceResult,
        flowElements,
        (I) flownodeInstance,
        processInstance,
        inputFlowId,
        flowNodeInstanceVariables,
        processingStatistics);

    if (flownodeInstance.isCompleted()) {
      processingStatistics.increaseFlowNodesFinished();
    }

    selectNextNodeIfAllowedStart(
        processInstance,
        (I) flownodeInstance,
        directInstanceResult,
        flowNodeInstanceVariables,
        flowNodeInstances);

    instanceResult.addInstanceUpdate(
        createFlowNodeInstanceUpdate(
            processInstance,
            flownodeInstance,
            flowNodeInstanceVariables,
            now));
  }

  public final void processContinue(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ProcessInstance processInstance,
      FlowNodeInstance<?> flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope variables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {
    if (!flowNodeInstance.stateAllowsContinue()) {
      return;
    }

    processingStatistics.increaseFlowNodesContinued();

    long now = clock.instant().toEpochMilli();

    this.processContinueSpecificFlowNodeInstance(
        flowNodeInstanceStore,
        instanceResult,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        processInstance,
        (I) flowNodeInstance,
        (C) trigger,
        variables,
        flowNodeInstances,
        processingStatistics);

    if (flowNodeInstance.isCompleted()) {
      processingStatistics.increaseFlowNodesFinished();
    }

    selectNextNodeIfAllowedContinue(
        (I) flowNodeInstance, processInstance, directInstanceResult, variables, flowNodeInstances);

    instanceResult.addInstanceUpdate(
        createFlowNodeInstanceUpdate(
            processInstance,
            flowNodeInstance,
            variables,
            now));
  }

  public void processTerminate(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowNodeInstance<?> instance,
      ProcessInstance processInstance,
      VariableScope variables,
      FlowNodeInstances flowNodeInstances,
      ProcessingStatistics processingStatistics) {
    // Only terminate if the instance is ready or waiting
    if (instance.stateAllowsTerminate()) {
      long now = clock.instant().toEpochMilli();

      processTerminateSpecificFlowNodeInstance(
          flowNodeInstanceStore,
          instanceResult,
          directInstanceResult,
          (I) instance,
          processInstance,
          variables,
          processingStatistics);

      instance.terminate();

      processingStatistics.increaseFlowNodesFinished();

      instanceResult.addInstanceUpdate(
          createFlowNodeInstanceUpdate(
              processInstance,
              instance,
              variables,
              now));
    }
  }

  protected void addInputVariablesToScope(
      E flowNode, VariableScope flowNodeInstanceVariables) {
    if (flowNode instanceof WithIoMapping withIoMapping) {
      ioMappingProcessor.addInputVariables(withIoMapping, flowNodeInstanceVariables);
    }
  }

  protected void selectNextNodeIfAllowedStart(
      ProcessInstance processInstance,
      I flownodeInstance,
      DirectInstanceResult directInstanceResult,
      VariableScope processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeStart()) {

      processNodeResultAndSelectNextInstance(
          processInstance,
          flownodeInstance,
          directInstanceResult,
          processInstanceVariables,
          flowNodeInstances);
    }
  }

  protected void selectNextNodeIfAllowedContinue(
      I flownodeInstance,
      ProcessInstance processInstance,
      DirectInstanceResult directInstanceResult,
      VariableScope processInstanceVariables,
      FlowNodeInstances flowNodeInstances) {
    if (flownodeInstance.canSelectNextNodeContinue()) {

      processNodeResultAndSelectNextInstance(
          processInstance,
          flownodeInstance,
          directInstanceResult,
          processInstanceVariables,
          flowNodeInstances);
    }
  }

  protected void processNodeResultAndSelectNextInstance(
      ProcessInstance processInstance,
      I flownodeInstance,
      DirectInstanceResult directInstanceResult,
      VariableScope flowNodeInstanceVariables,
      FlowNodeInstances flowNodeInstances) {
    FlowNode flowNode = flownodeInstance.getFlowNode();
    if (flowNode instanceof WithIoMapping withIoMapping) {
      addOutputVariables(flowNodeInstanceVariables, withIoMapping);
    }

    flownodeInstance.increasePassedCnt();
    getSelectedSequenceFlows(
            processInstance, flownodeInstance, flowNodeInstances, flowNodeInstanceVariables)
        .forEach(
            sequenceFlow -> {
              FlowNodeInstance<?> newFlowNodeInstance =
                  sequenceFlow
                      .getTargetNode()
                      .createAndStoreNewInstance(
                          flownodeInstance.getParentInstance(), flowNodeInstances);
              directInstanceResult.addNewFlowNodeInstance(
                  processInstance,
                  new FlowNodeInstanceInfo(newFlowNodeInstance, sequenceFlow.getId()));
            });
  }

  protected abstract Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope variables);

  protected void addOutputVariables(
      VariableScope processInstanceVariables, WithIoMapping withIoMapping) {
    ioMappingProcessor.addOutputVariables(withIoMapping, processInstanceVariables);
  }

  protected abstract void processStartSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      FlowNodeInstances flowNodeInstances,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      ProcessInstance processInstance,
      String inputFlowId,
      VariableScope variables,
      ProcessingStatistics processingStatistics);

  protected abstract void processContinueSpecificFlowNodeInstance(
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
      ProcessingStatistics processingStatistics);

  protected abstract void processTerminateSpecificFlowNodeInstance(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      I instance,
      ProcessInstance processInstance,
      VariableScope variables,
      ProcessingStatistics processingStatistics);

  protected InstanceUpdate createFlowNodeInstanceUpdate(
      ProcessInstance processInstance,
      FlowNodeInstance<?> flowNodeInstance,
      VariableScope variables,
      long processTime) {
    List<Long> elementInstanceIdPath = flowNodeInstance.getKeyPath();
    VariablesDTO processInstanceVariablesDTO = variables.scopeToDTO();
    FlowNodeInstanceDTO flowNodeInstanceDTO = processInstanceMapper.map(flowNodeInstance);
    return new InstanceUpdate(processInstance.getProcessInstanceKey(),
        new FlowNodeInstanceUpdateDTO(
          elementInstanceIdPath,
          flowNodeInstanceDTO,
          processInstanceVariablesDTO,
          processTime));
  }
}
