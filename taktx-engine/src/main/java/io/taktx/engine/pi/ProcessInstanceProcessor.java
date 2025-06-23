/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import static io.taktx.dto.Constants.MAX_LONG;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.FlowElementDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.FlowNodeInstancesDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ProcessInstanceState;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.dto.TerminateTriggerDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.VariableKeyDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.TopicMonitor;
import io.taktx.engine.pd.Stores;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithFlowNodeInstances;
import io.taktx.engine.pi.processor.IoMappingProcessor;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

@Slf4j
@RequiredArgsConstructor
public class ProcessInstanceProcessor
    implements Processor<UUID, ProcessInstanceTriggerDTO, Object, Object> {

  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final Forwarder forwarder;
  private final IoMappingProcessor ioMappingProcessor;
  private final TaktConfiguration taktConfiguration;
  private final FlowNodeInstancesProcessor flowNodeInstancesProcessor;
  private final Clock clock;
  private final DtoMapper dtoMapper;
  private final ProcessingStatistics processingStatistics;
  private final TopicMonitor topicMonitor;
  private ReadOnlyKeyValueStore<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>>
      definitionsStore;
  private ReadOnlyKeyValueStore<String, ValueAndTimestamp<TopicMetaDTO>> externalTaskMetaStore;

  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private KeyValueStore<VariableKeyDTO, JsonNode> variablesStore;
  private ProcessorContext<Object, Object> context;
  private final Map<ProcessDefinitionKey, FlowElements> flowElementsCache = new HashMap<>();
  private final Map<ProcessDefinitionKey, ProcessDefinitionDTO> definitionsCache = new HashMap<>();

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.definitionsStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()));
    this.externalTaskMetaStore =
        context.getStateStore(taktConfiguration.getPrefixed(Stores.TOPIC_META.getStorename()));
    this.variablesStore =
        context.getStateStore(taktConfiguration.getPrefixed(Stores.VARIABLES.getStorename()));
    this.processInstanceStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()));
    this.flowNodeInstanceStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.FLOW_NODE_INSTANCE.getStorename()));
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTriggerDTO> triggerRecord) {
    ProcessInstanceTriggerDTO trigger = triggerRecord.value();

    try {
      switch (trigger) {
        case StartCommandDTO startCommand ->
            processStartCommandRecord(triggerRecord.key(), startCommand);
        case StartFlowElementTriggerDTO starteFlowElementTrigger ->
            handleStartFlowElement(starteFlowElementTrigger);
        case ContinueFlowElementTriggerDTO continueFlowElementTrigger2 ->
            handleContinue(continueFlowElementTrigger2);
        case TerminateTriggerDTO terminateTrigger ->
            handleTerminate(flowNodeInstanceStore, terminateTrigger);
        default ->
            throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
      }
    } catch (ProcessInstanceException e) {
      handleExceptional(flowNodeInstanceStore, e);
    } catch (Throwable t) { // NOSONAR
      log.error("Internal error occurred for", t);
    }
  }

  private void processStartCommandRecord(UUID processInstanceKey, StartCommandDTO startCommand) {
    processingStatistics.startTimerForProcessInstance(processInstanceKey);
    processingStatistics.increaseProcessInstancesStarted();

    ProcessDefinitionDTO processDefinition =
        getProcessDefinitionDTO(startCommand.getProcessDefinitionKey());

    String startNodeId = startCommand.getElementId();

    FlowElementDTO startNode =
        processDefinition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getStartNode(startNodeId);

    if (startNode == null) {
      log.info("No start {} node found for process definition {}", startNodeId, processDefinition);
      return;
    }

    String startEventId = startNode.getId();

    FlowNodeInstances flowNodeInstances = new FlowNodeInstances();

    VariableScope processInstanceVariables =
        new VariableScope(variablesStore, processInstanceKey, null, null);

    processInstanceVariables.merge(startCommand.getVariables());

    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(processDefinition);

    Set<IoVariableMapping> ioVariableMappings = dtoMapper.map(startCommand.getOutputMappings());

    ProcessInstance processInstance =
        new ProcessInstance(
            processInstanceKey,
            startCommand.getParentProcessInstanceKey(),
            startCommand.getParentElementInstancePath(),
            processDefinitionKey,
            flowNodeInstances,
            startCommand.isPropagateAllToParent(),
            ioVariableMappings);

    InstanceResult instanceResult = InstanceResult.empty();

    VariablesDTO updateVariablesAtStart = processInstanceVariables.scopeToDTO();
    instanceResult.addInstanceUpdate(
        processInstanceToUpdate(
            processInstance, flowNodeInstances, updateVariablesAtStart, clock.millis()));

    FlowElements flowElements = getFlowElements(processDefinitionKey);

    ProcessInstanceProcessingContext processInstanceProcessingContext =
        createProcessInstanceProcessingContext(processInstance, instanceResult);

    FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(flowNodeInstances, 0, flowElements);
    flowNodeInstancesProcessor.processStart(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        startEventId,
        null,
        processInstanceVariables);

    processResultAndForward(
        processInstanceProcessingContext,
        processDefinitionKey,
        flowNodeInstances,
        processInstanceVariables,
        flowElements,
        updateVariablesAtStart);
  }

  private ProcessInstanceProcessingContext createProcessInstanceProcessingContext(
      ProcessInstance processInstance, InstanceResult instanceResult) {
    return ProcessInstanceProcessingContext.builder()
        .processInstance(processInstance)
        .processingStatistics(processingStatistics)
        .instanceResult(instanceResult)
        .flowNodeInstanceStore(flowNodeInstanceStore)
        .externalTaskMetaStore(externalTaskMetaStore)
        .topicStore(topicMonitor)
        .build();
  }

  private ProcessDefinitionDTO getProcessDefinitionDTO(ProcessDefinitionKey processDefinitionKey) {
    return definitionsCache.computeIfAbsent(
        processDefinitionKey, key -> definitionsStore.get(key).value());
  }

  private void handleExceptional(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      ProcessInstanceException e) {
    FlowNodeInstance<?> flowNodeInstance = e.getFlowNodeInstance();
    flowNodeInstance.terminate();
    ProcessInstance processInstance = e.getProcessInstance();
    log.warn(
        "Takt exception occurred for processinstance {}, {}, {}: {}",
        processInstance.getProcessInstanceKey(),
        flowNodeInstance.getFlowNode().getId(),
        flowNodeInstance.getElementInstanceId(),
        e.getMessage());
    handleTerminate(
        flowNodeInstanceStore,
        new TerminateTriggerDTO(processInstance.getProcessInstanceKey(), List.of()));
  }

  private InstanceUpdate processInstanceToUpdate(
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      VariablesDTO variables,
      long processTime) {

    FlowNodeInstancesDTO flowNodeInstancesDTO = flowNodeInstancesToDTO(flowNodeInstances);
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder()
            .flowNodeInstances(flowNodeInstancesDTO)
            .build();

    return new InstanceUpdate(
        processInstance.getProcessInstanceKey(),
        new ProcessInstanceUpdateDTO(processInstanceDTO, variables, processTime));
  }

  public void handleStartFlowElement(StartFlowElementTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceKey = trigger.getProcessInstanceKey();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceKey);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      VariableScope processInstanceVariables =
          new VariableScope(variablesStore, processInstanceKey, null, null);
      mergeVariablesInScope(
          processInstanceVariables,
          trigger.getParentElementInstanceIdPath(),
          trigger.getVariables());
      ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
      ProcessInstanceProcessingContext processInstanceProcessingContext =
          createProcessInstanceProcessingContext(processInstance, instanceResult);

      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext =
          new FlowNodeInstanceProcessingContext(
              processInstance.getFlowNodeInstances(), 0, flowElements);

      flowNodeInstancesProcessor.processStartFlowElement(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          trigger,
          processInstanceVariables);

      processResultAndForward(
          processInstanceProcessingContext,
          processInstance.getProcessDefinitionKey(),
          processInstance.getFlowNodeInstances(),
          processInstanceVariables,
          flowElements,
          VariablesDTO.empty());
    }
  }

  public void handleContinue(ContinueFlowElementTriggerDTO trigger) {

    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceKey = trigger.getProcessInstanceKey();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceKey);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {

        VariableScope processInstanceVariables =
            new VariableScope(variablesStore, processInstanceKey, null, null);

        mergeVariablesInScope(
            processInstanceVariables, trigger.getElementInstanceIdPath(), trigger.getVariables());

        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            createProcessInstanceProcessingContext(processInstance, instanceResult);

        FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext =
            new FlowNodeInstanceProcessingContext(
                processInstance.getFlowNodeInstances(), 0, flowElements);

        flowNodeInstancesProcessor.processContinue(
            processInstanceProcessingContext,
            flowNodeInstanceProcessingContext,
            trigger,
            processInstanceVariables);

        processResultAndForward(
            processInstanceProcessingContext,
            processInstance.getProcessDefinitionKey(),
            processInstance.getFlowNodeInstances(),
            processInstanceVariables,
            flowElements,
            VariablesDTO.empty());
      }
    } else {
      log.warn("Process instanceToContinue not found for key: {}", processInstanceKey);
    }
  }

  private void mergeVariablesInScope(
      VariableScope processInstanceVariables,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables) {
    VariableScope targetScope = processInstanceVariables;
    if (elementInstanceIdPath != null) {
      // merge into the nearest parent scope
      for (int i = 0; i < elementInstanceIdPath.size() - 2; i++) {
        Long elementInstanceId = elementInstanceIdPath.get(i);
        targetScope = targetScope.selectFlowNodeInstancesScope(elementInstanceId);
      }
    }
    targetScope.merge(variables);
  }

  private FlowElements getFlowElements(ProcessDefinitionKey processDefinitionKey) {
    return flowElementsCache.computeIfAbsent(
        processDefinitionKey,
        ignored -> {
          ProcessDefinitionDTO processDefinitionDTO = getProcessDefinitionDTO(processDefinitionKey);
          if (processDefinitionDTO != null) {
            return definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions());
          }
          return null;
        });
  }

  private void handleTerminate(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      TerminateTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {
        VariableScope processInstanceVariables =
            new VariableScope(
                variablesStore, processInstanceDTO.getProcessInstanceKey(), null, null);
        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            ProcessInstanceProcessingContext.builder()
                .flowNodeInstanceStore(flowNodeInstanceStore)
                .processInstance(processInstance)
                .processingStatistics(processingStatistics)
                .instanceResult(instanceResult)
                .topicStore(topicMonitor)
                .build();

        FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext =
            new FlowNodeInstanceProcessingContext(
                processInstance.getFlowNodeInstances(), 0, flowElements);

        flowNodeInstancesProcessor.processTerminate(
            processInstanceProcessingContext,
            flowNodeInstanceProcessingContext,
            trigger,
            processInstanceVariables);

        processResultAndForward(
            processInstanceProcessingContext,
            processInstance.getProcessDefinitionKey(),
            processInstance.getFlowNodeInstances(),
            processInstanceVariables,
            flowElements,
            VariablesDTO.empty());
      }
    }
  }

  private void processResultAndForward(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      ProcessDefinitionKey processDefinitionKey,
      FlowNodeInstances flowNodeInstances,
      VariableScope processInstanceVariables,
      FlowElements flowElements,
      VariablesDTO updatedVariablesAtStart) {

    FlowNodeInstancesDTO flowNodeInstancesDTO = flowNodeInstancesToDTO(flowNodeInstances);

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder()
            .flowNodeInstances(flowNodeInstancesDTO)
            .build();

    processInstanceVariables.persist();
    InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();

    if (flowNodeInstances.isStateChanged()) {
      if (flowNodeInstances.getState() == ProcessInstanceState.COMPLETED) {
        processingStatistics.increaseProcessInstancesFinished();
      }
      VariablesDTO currentVariablesDTO = processInstanceVariables.scopeToDTO();

      VariablesDTO variablesChangedAfterStart = currentVariablesDTO.diff(updatedVariablesAtStart);

      instanceResult.addInstanceUpdate(
          processInstanceToUpdate(
              processInstance, flowNodeInstances, variablesChangedAfterStart, clock.millis()));
    }

    if (processInstance.getFlowNodeInstances().getState().isFinished()) {
      continueParentInstance(instanceResult, processInstance, processInstanceVariables);
    }

    forwarder.forward(context, instanceResult, processDefinitionKey, processInstanceDTO);

    if (processInstance.getFlowNodeInstances().getState().isFinished()) {
      purgeProcessInstance(processInstanceDTO);
    } else {
      processInstanceStore.put(processInstance.getProcessInstanceKey(), processInstanceDTO);
      storeFlowNodeInstances(
          processInstance.getProcessInstanceKey(),
          processInstance.getFlowNodeInstances(),
          flowElements);
    }
  }

  private void continueParentInstance(
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      VariableScope processInstanceVariables) {
    if (processInstance.getParentProcessInstanceKey() != null) {
      VariablesDTO variables;

      if (processInstance.isPropagateAllToParent()) {
        variables = VariablesDTO.of(processInstanceVariables.retrieveAndFlattenAll());
      } else {
        VariableScope outputVariables =
            new VariableScope(
                processInstanceVariables.getVariableStore(),
                processInstance.getProcessInstanceKey(),
                null,
                null);
        ioMappingProcessor.addVariables(outputVariables, processInstance.getOutputMappings());
        variables = VariablesDTO.of(outputVariables.getVariables());
      }

      instanceResult.addContinuation(
          new ContinueFlowElementTriggerDTO(
              processInstance.getParentProcessInstanceKey(),
              processInstance.getParentElementInstancePath(),
              null,
              variables));
    }
  }

  private FlowNodeInstancesDTO flowNodeInstancesToDTO(FlowNodeInstances flowNodeInstances) {
    return new FlowNodeInstancesDTO(
        flowNodeInstances.getState(),
        flowNodeInstances.getActiveCnt(),
        flowNodeInstances.getElementInstanceCnt(),
        flowNodeInstances.getGatewayInstances(),
        flowNodeInstances.getMessageSubscriptions(),
        flowNodeInstances.getScheduleKeys());
  }

  private void storeFlowNodeInstances(
      UUID processInstanceKey, FlowNodeInstances flowNodeInstances, FlowElements flowElements) {
    for (FlowNodeInstance<?> fLowNodeInstance : flowNodeInstances.getInstances().values()) {
      storeFlowNodeInstance(processInstanceKey, fLowNodeInstance, flowElements);
    }
  }

  private void storeFlowNodeInstance(
      UUID processInstanceKey, FlowNodeInstance<?> fLowNodeInstance, FlowElements flowElements) {
    if (fLowNodeInstance.isDirty()) {
      FlowNodeInstanceDTO flowNodeInstanceDTO = instanceMapper.map(fLowNodeInstance, flowElements);
      FlowNodeInstanceKeyDTO key =
          new FlowNodeInstanceKeyDTO(processInstanceKey, fLowNodeInstance.createKeyPath());
      flowNodeInstanceStore.put(key, flowNodeInstanceDTO);
    }

    if (fLowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances) {
      storeFlowNodeInstances(
          processInstanceKey, withFlowNodeInstances.getFlowNodeInstances(), flowElements);
    }
  }

  private void purgeProcessInstance(ProcessInstanceDTO processInstance) {
    UUID processInstanceKey = processInstance.getProcessInstanceKey();
    this.processingStatistics.stopTimerForProcessInstance(
        processInstanceKey, processInstance.getProcessDefinitionKey().getProcessDefinitionId());

    this.processInstanceStore.delete(processInstanceKey);

    FlowNodeInstanceKeyDTO start = new FlowNodeInstanceKeyDTO(processInstanceKey, List.of());
    FlowNodeInstanceKeyDTO end = new FlowNodeInstanceKeyDTO(processInstanceKey, List.of(MAX_LONG));

    try (KeyValueIterator<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> range =
        this.flowNodeInstanceStore.range(start, end)) {
      range.forEachRemaining(r -> this.flowNodeInstanceStore.delete(r.key));
    }
    FlowNodeInstanceKeyDTO flowNodeInstanceKeyStart =
        new FlowNodeInstanceKeyDTO(processInstanceKey, List.of());
    VariableKeyDTO variableStartKey = new VariableKeyDTO(flowNodeInstanceKeyStart, "");
    FlowNodeInstanceKeyDTO flownodeInstanceKeyEnd =
        new FlowNodeInstanceKeyDTO(processInstanceKey, List.of(MAX_LONG));
    VariableKeyDTO variableEndKey = new VariableKeyDTO(flownodeInstanceKeyEnd, "\uffff");

    try (KeyValueIterator<VariableKeyDTO, JsonNode> range =
        this.variablesStore.range(variableStartKey, variableEndKey)) {
      range.forEachRemaining(r -> this.variablesStore.delete(r.key));
    }
  }
}
