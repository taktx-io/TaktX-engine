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
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.FlowElementDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.dto.VariableKeyDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithScope;
import io.taktx.engine.pi.processor.IoMappingProcessor;
import io.taktx.engine.topicmanagement.DynamicTopicManager;
import java.time.Clock;
import java.util.Collections;
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
  private final DefinitionsCache definitionsCache;
  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final Forwarder forwarder;
  private final IoMappingProcessor ioMappingProcessor;
  private final TaktConfiguration taktConfiguration;
  private final ScopeProcessor scopeProcessor;
  private final Clock clock;
  private final DtoMapper dtoMapper;
  private final ProcessingStatistics processingStatistics;
  private final DynamicTopicManager topicManager;

  private final Map<ProcessDefinitionKey, FlowElements> flowElementsCache = new HashMap<>();

  private ReadOnlyKeyValueStore<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>>
      definitionsStore;
  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private KeyValueStore<VariableKeyDTO, JsonNode> variablesStore;
  private ProcessorContext<Object, Object> context;

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.definitionsStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()));
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

    // Start timing for P99 investigation
    long kafkaTimestamp = triggerRecord.timestamp();

    try {
      switch (trigger) {
        case StartCommandDTO startCommand -> {
          // Record end-to-end latency using Kafka timestamp
          processingStatistics.recordProcessInstanceLatency(
              kafkaTimestamp, trigger.getClass().getSimpleName());

          processStartCommandRecord(triggerRecord.key(), startCommand);
        }
        case StartFlowElementTriggerDTO starteFlowElementTrigger -> {
          // Record end-to-end latency using Kafka timestamp
          processingStatistics.recordProcessInstanceLatency(
              kafkaTimestamp, trigger.getClass().getSimpleName());

          handleStartFlowElement(starteFlowElementTrigger);
        }
        case ContinueFlowElementTriggerDTO continueFlowElementTrigger2 -> {
          processingStatistics.recordExternalTaskResponseLatency(
              kafkaTimestamp, continueFlowElementTrigger2.getClass().getSimpleName());
          handleContinue(continueFlowElementTrigger2);
        }
        case AbortTriggerDTO terminateTrigger -> {
          // Record end-to-end latency using Kafka timestamp
          processingStatistics.recordProcessInstanceLatency(
              kafkaTimestamp, trigger.getClass().getSimpleName());

          handleTerminate(flowNodeInstanceStore, terminateTrigger);
        }
        default ->
            throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
      }
    } catch (ProcessInstanceException e) {
      handleExceptional(flowNodeInstanceStore, e);
    } catch (Throwable t) { // NOSONAR
      log.error("Internal error occurred for", t);
    }
  }

  private void processStartCommandRecord(UUID processInstanceId, StartCommandDTO startCommand) {
    processingStatistics.startTimerForProcessInstance(processInstanceId);
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

    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(processDefinition);
    FlowElements flowElements = getFlowElements(processDefinitionKey);
    Scope scope =
        new Scope(
            null,
            processInstanceId,
            null,
            flowElements,
            instanceMapper,
            variablesStore,
            flowNodeInstanceStore);

    Set<IoVariableMapping> ioVariableMappings = dtoMapper.map(startCommand.getOutputMappings());

    ProcessInstance processInstance =
        new ProcessInstance(
            processInstanceId,
            startCommand.getParentProcessInstanceId(),
            startCommand.getParentElementInstancePath(),
            processDefinitionKey,
            scope,
            startCommand.isPropagateAllToParent(),
            ioVariableMappings);

    InstanceResult instanceResult = InstanceResult.empty();

    instanceResult.addInstanceUpdate(
        processInstanceToUpdate(processInstance, scope, clock.millis()));

    ProcessInstanceProcessingContext processInstanceProcessingContext =
        createProcessInstanceProcessingContext(processInstance, instanceResult);

    scopeProcessor.processStart(
        Collections.emptyList(),
        startEventId,
        startCommand.getVariables(),
        processInstanceProcessingContext,
        scope);

    processResultAndForward(processInstanceProcessingContext, processDefinitionKey, scope);
  }

  private ProcessInstanceProcessingContext createProcessInstanceProcessingContext(
      ProcessInstance processInstance, InstanceResult instanceResult) {
    return ProcessInstanceProcessingContext.builder()
        .processInstance(processInstance)
        .processingStatistics(processingStatistics)
        .instanceResult(instanceResult)
        .flowNodeInstanceStore(flowNodeInstanceStore)
        .topicManager(topicManager)
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
    flowNodeInstance.abort();
    ProcessInstance processInstance = e.getProcessInstance();
    log.warn(
        "Takt exception occurred for processinstance {}, {}, {}: {}",
        processInstance.getProcessInstanceId(),
        flowNodeInstance.getFlowNode().getId(),
        flowNodeInstance.getElementInstanceId(),
        e.getMessage());
    handleTerminate(
        flowNodeInstanceStore,
        new AbortTriggerDTO(processInstance.getProcessInstanceId(), List.of()));
  }

  private InstanceUpdate processInstanceToUpdate(
      ProcessInstance processInstance, Scope scope, long processTime) {

    ScopeDTO scopeDTO = scopeToDTO(scope);
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder().scope(scopeDTO).build();

    return new InstanceUpdate(
        processInstance.getProcessInstanceId(),
        new ProcessInstanceUpdateDTO(processInstanceDTO, VariablesDTO.empty(), processTime));
  }

  public void handleStartFlowElement(StartFlowElementTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
      enrichScope(processInstance, processInstanceId, flowElements);

      ProcessInstanceProcessingContext processInstanceProcessingContext =
          createProcessInstanceProcessingContext(processInstance, instanceResult);

      scopeProcessor.processStart(
          trigger.getParentElementInstanceIdPath(),
          trigger.getElementId(),
          trigger.getVariables(),
          processInstanceProcessingContext,
          processInstance.getScope());

      processResultAndForward(
          processInstanceProcessingContext,
          processInstance.getProcessDefinitionKey(),
          processInstance.getScope());
    }
  }

  public void handleContinue(ContinueFlowElementTriggerDTO trigger) {

    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {

        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
        enrichScope(processInstance, processInstanceId, flowElements);

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            createProcessInstanceProcessingContext(processInstance, instanceResult);

        scopeProcessor.processContinue(
            processInstanceProcessingContext, processInstance.getScope(), trigger, trigger.getElementInstanceIdPath());

        processResultAndForward(
            processInstanceProcessingContext,
            processInstance.getProcessDefinitionKey(),
            processInstance.getScope());
      }
    } else {
      log.warn("Process instanceToContinue not found for key: {}", processInstanceId);
    }
  }

  private void handleTerminate(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      AbortTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId1 = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId1);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {
        UUID processInstanceId = processInstanceDTO.getProcessInstanceId();
        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
        enrichScope(processInstance, processInstanceId, flowElements);

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            createProcessInstanceProcessingContext(processInstance, instanceResult);

        scopeProcessor.processAbort(processInstanceProcessingContext, processInstance.getScope(), trigger);

        processResultAndForward(
            processInstanceProcessingContext,
            processInstance.getProcessDefinitionKey(),
            processInstance.getScope());
      }
    }
  }

  private void processResultAndForward(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      ProcessDefinitionKey processDefinitionKey,
      Scope scope) {

      EventSignal eventSignal = scope.getDirectInstanceResult().pollBubbleUpEvent();
      while(eventSignal != null) {
            if (eventSignal instanceof ErrorEventSignal ) {
                AbortTriggerDTO trigger = new AbortTriggerDTO(processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(), Collections.emptyList());
                scopeProcessor.processAbort(processInstanceProcessingContext, scope, trigger);
                break;
            }
          eventSignal = scope.getDirectInstanceResult().pollBubbleUpEvent();
      }

      ScopeDTO scopeDTO = scopeToDTO(scope);

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder().scope(scopeDTO).build();

    scope.getVariableScope().persist();

    InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();

    if (scope.isStateChanged()) {
      if (scope.getState() == ExecutionState.COMPLETED) {
        processingStatistics.increaseProcessInstancesFinished();
      }

      instanceResult.addInstanceUpdate(
          processInstanceToUpdate(processInstance, scope, clock.millis()));
    }

    if (processInstance.getScope().getState().isDone()) {
      continueParentInstance(instanceResult, processInstance, scope.getVariableScope());
    }

    forwarder.forward(context, instanceResult, processDefinitionKey, processInstanceDTO);

    if (processInstance.getScope().getState().isDone()) {
      purgeProcessInstance(processInstanceDTO);
    } else {
      processInstanceStore.put(processInstance.getProcessInstanceId(), processInstanceDTO);
      storeScope(processInstance.getProcessInstanceId(), processInstance.getScope());
    }
  }

  private FlowElements getFlowElements(ProcessDefinitionKey processDefinitionKey) {
    // First, get the ProcessDefinitionDTO outside of computeIfAbsent to avoid recursive cache calls
    ProcessDefinitionDTO processDefinitionDTO = getProcessDefinitionDTO(processDefinitionKey);
    if (processDefinitionDTO == null) {
      return null;
    }

    return flowElementsCache.computeIfAbsent(
        processDefinitionKey,
        ignored -> definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions()));
  }

  private void storeScope(UUID processInstanceId, Scope scope) {
    for (FlowNodeInstance<?> fLowNodeInstance :
        scope.getFlowNodeInstances().getInstances().values()) {
      storeFlowNodeInstance(processInstanceId, fLowNodeInstance, scope);
    }
  }

  private void storeFlowNodeInstance(
      UUID processInstanceId, FlowNodeInstance<?> fLowNodeInstance, Scope scope) {
    if (fLowNodeInstance.isDirty()) {
      FlowNodeInstanceDTO flowNodeInstanceDTO =
          instanceMapper.map(fLowNodeInstance, scope.getFlowElements());
      FlowNodeInstanceKeyDTO key =
          new FlowNodeInstanceKeyDTO(processInstanceId, fLowNodeInstance.createKeyPath());
      flowNodeInstanceStore.put(key, flowNodeInstanceDTO);
    }

    if (fLowNodeInstance instanceof WithScope withScope) {
      storeScope(processInstanceId, withScope.getScope());
    }
  }

  private void continueParentInstance(
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      VariableScope processInstanceVariables) {
    if (processInstance.getParentProcessInstanceId() != null) {
      VariablesDTO variables;

      if (processInstance.isPropagateAllToParent()) {
        variables = VariablesDTO.of(processInstanceVariables.retrieveAndFlattenAll());
      } else {
        VariableScope outputVariables =
            new VariableScope(null, processInstance.getProcessInstanceId(), null, variablesStore);
        ioMappingProcessor.addVariables(outputVariables, processInstance.getOutputMappings());
        variables = VariablesDTO.of(outputVariables.getVariables());
      }

      instanceResult.addContinuation(
          new ContinueFlowElementTriggerDTO(
              processInstance.getParentProcessInstanceId(),
              processInstance.getParentElementInstancePath(),
              null,
              variables));
    }
  }

  private void enrichScope(
      ProcessInstance processInstance, UUID processInstanceId, FlowElements flowElements) {
    Scope scope = processInstance.getScope();
    scope.setParentScope(null);
    scope.setProcessInstanceId(processInstanceId);
    VariableScope parentVariableScope = null;
    VariableScope variableScope =
        new VariableScope(parentVariableScope, processInstanceId, null, variablesStore);
    scope.setVariableScope(variableScope);
    scope.setFlowNodeInstances(
        new FlowNodeInstances(
            processInstanceId,
            null,
            flowElements,
            instanceMapper,
            flowNodeInstanceStore,
            variablesStore));
    scope.setParentFlowNodeInstance(null);
  }

  private ScopeDTO scopeToDTO(Scope scope) {
    return new ScopeDTO(
        scope.getState(),
        scope.getActiveCnt(),
        scope.getSubProcessLevel(),
        scope.getElementInstanceCnt(),
        scope.getGatewayInstances(),
        scope.getMessageSubscriptions(),
        scope.getScheduleKeys(),
        scope.getActivityToBoundaryEvents());
  }

  private void purgeProcessInstance(ProcessInstanceDTO processInstance) {
    UUID processInstanceId = processInstance.getProcessInstanceId();
    this.processingStatistics.stopTimerForProcessInstance(
        processInstanceId, processInstance.getProcessDefinitionKey().getProcessDefinitionId());

    this.processInstanceStore.delete(processInstanceId);

    FlowNodeInstanceKeyDTO start = new FlowNodeInstanceKeyDTO(processInstanceId, List.of());
    FlowNodeInstanceKeyDTO end = new FlowNodeInstanceKeyDTO(processInstanceId, List.of(MAX_LONG));

    try (KeyValueIterator<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> range =
        this.flowNodeInstanceStore.range(start, end)) {
      range.forEachRemaining(r -> this.flowNodeInstanceStore.delete(r.key));
    }
    FlowNodeInstanceKeyDTO flowNodeInstanceKeyStart =
        new FlowNodeInstanceKeyDTO(processInstanceId, List.of());
    VariableKeyDTO variableStartKey = new VariableKeyDTO(flowNodeInstanceKeyStart, "");
    FlowNodeInstanceKeyDTO flownodeInstanceKeyEnd =
        new FlowNodeInstanceKeyDTO(processInstanceId, List.of(MAX_LONG));
    VariableKeyDTO variableEndKey = new VariableKeyDTO(flownodeInstanceKeyEnd, "\uffff");

    try (KeyValueIterator<VariableKeyDTO, JsonNode> range =
        this.variablesStore.range(variableStartKey, variableEndKey)) {
      range.forEachRemaining(r -> this.variablesStore.delete(r.key));
    }
  }
}
