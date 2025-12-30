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
import io.taktx.dto.EventSignalDTO;
import io.taktx.dto.EventSignalTriggerDTO;
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
import io.taktx.dto.SetVariableTriggerDTO;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
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
  private final PathExtractor pathExtractor;
  private final Map<ProcessDefinitionKey, FlowElements> flowElementsCache = new HashMap<>();

  private ReadOnlyKeyValueStore<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>>
      definitionsStore;
  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private KeyValueStore<VariableKeyDTO, JsonNode> variablesStore;
  private ProcessorContext<Object, Object> context;
  private ThreadLocal<ProcessInstance> processInstanceThreadLocal = new ThreadLocal<>();
  private ThreadLocal<ProcessInstanceProcessingContext>
      processInstanceProcessingContextThreadLocal = new ThreadLocal<>();
  private ThreadLocal<ProcessDefinitionKey> processDefinitionKeyThreadLocal = new ThreadLocal<>();

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
        case SetVariableTriggerDTO setVariableTrigger -> {
          // Record end-to-end latency using Kafka timestamp
          processingStatistics.recordProcessInstanceLatency(
              kafkaTimestamp, trigger.getClass().getSimpleName());

          handleSetVariables(setVariableTrigger);
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

          handleTerminate(terminateTrigger);
        }
        default ->
            throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
      }
    } catch (Throwable t) { // NOSONAR
      handleIncident(trigger, t);
      log.error("Internal error occurred for", t);
    } finally {
      processDefinitionKeyThreadLocal.remove();
      processInstanceThreadLocal.remove();
      processInstanceProcessingContextThreadLocal.remove();
    }
  }

  private void handleIncident(ProcessInstanceTriggerDTO trigger, Throwable t) {
    ProcessInstance processInstance = processInstanceThreadLocal.get();
    ProcessDefinitionKey processDefinitionKey = processDefinitionKeyThreadLocal.get();

    ProcessInstanceProcessingContext processInstanceProcessingContext =
        processInstanceProcessingContextThreadLocal.get();
    if (processInstance != null && processInstanceProcessingContext != null) {
      Scope scope = processInstance.getScope();
      processInstance.setIncidentInfo(
          new IncidentInfo(
              null,
              t.getMessage(),
              Arrays.stream(t.getStackTrace())
                  .map(StackTraceElement::toString)
                  .toArray(String[]::new)));
      processResultAndForward(processInstanceProcessingContext, processDefinitionKey, scope);
    }
  }

  private void processStartCommandRecord(UUID processInstanceId, StartCommandDTO startCommand) {
    processingStatistics.startTimerForProcessInstance(processInstanceId);
    processingStatistics.increaseProcessInstancesStarted();

    ProcessDefinitionDTO processDefinition =
        getProcessDefinitionDTO(startCommand.getProcessDefinitionKey());
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(processDefinition);
    processDefinitionKeyThreadLocal.set(processDefinitionKey);

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
    processInstanceThreadLocal.set(processInstance);

    InstanceResult instanceResult = InstanceResult.empty();

    instanceResult.addInstanceUpdate(startProcessInstanceToUpdate(processInstance, scope));

    ProcessInstanceProcessingContext processInstanceProcessingContext =
        createProcessInstanceProcessingContext(processInstance, instanceResult);
    processInstanceProcessingContextThreadLocal.set(processInstanceProcessingContext);

    doWhileCatching(
        processInstanceProcessingContext,
        processDefinitionKey,
        scope,
        () ->
            scopeProcessor.processStart(
                Collections.emptyList(),
                startEventId,
                startCommand.getVariables(),
                processInstanceProcessingContext,
                scope));
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

  private InstanceUpdate startProcessInstanceToUpdate(ProcessInstance processInstance, Scope scope) {

    ScopeDTO scopeDTO = scopeToDTO(scope);
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder().scope(scopeDTO).build();

    VariablesDTO variables = VariablesDTO.ofJsonMap(scope.getVariableScope().getVariables());

    return new InstanceUpdate(
        processInstance.getProcessInstanceId(),
        new ProcessInstanceUpdateDTO(
            processInstanceDTO, variables, clock.millis(), null));
  }

  private InstanceUpdate processInstanceToUpdate(ProcessInstance processInstance, Scope scope) {

    ScopeDTO scopeDTO = scopeToDTO(scope);
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder().scope(scopeDTO).build();

    VariablesDTO variables = VariablesDTO.ofJsonMap(scope.getVariableScope().getVariables());

    Long processStartTime =
        scope.isStateChanged() && scope.getState() == ExecutionState.ACTIVE ? clock.millis() : null;
    Long processEndTime =
        scope.isStateChanged()
                && (scope.getState() == ExecutionState.COMPLETED
                    || scope.getState() == ExecutionState.ABORTED)
            ? clock.millis()
            : null;
    return new InstanceUpdate(
        processInstance.getProcessInstanceId(),
        new ProcessInstanceUpdateDTO(
            processInstanceDTO, variables, processStartTime, processEndTime));
  }

  public void handleSetVariables(SetVariableTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
      enrichScope(processInstance.getScope(), null, processInstanceId, flowElements);
      ProcessDefinitionKey processDefinitionKey = processInstance.getProcessDefinitionKey();
      processDefinitionKeyThreadLocal.set(processDefinitionKey);

      ProcessInstanceProcessingContext processInstanceProcessingContext =
          createProcessInstanceProcessingContext(processInstance, instanceResult);

      doWhileCatching(
          processInstanceProcessingContext,
          processDefinitionKey,
          processInstance.getScope(),
          () ->
              scopeProcessor.processSetVariables(
                  trigger.getParentElementInstanceIdPath(),
                  trigger.getVariables(),
                  processInstanceProcessingContext,
                  processInstance.getScope()));
    }
  }

  public void handleStartFlowElement(StartFlowElementTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
      enrichScope(processInstance.getScope(), null, processInstanceId, flowElements);
      ProcessDefinitionKey processDefinitionKey = processInstance.getProcessDefinitionKey();
      processDefinitionKeyThreadLocal.set(processDefinitionKey);

      ProcessInstanceProcessingContext processInstanceProcessingContext =
          createProcessInstanceProcessingContext(processInstance, instanceResult);

      doWhileCatching(
          processInstanceProcessingContext,
          processDefinitionKey,
          processInstance.getScope(),
          () ->
              scopeProcessor.processStart(
                  trigger.getParentElementInstanceIdPath(),
                  trigger.getElementId(),
                  trigger.getVariables(),
                  processInstanceProcessingContext,
                  processInstance.getScope()));
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
        enrichScope(processInstance.getScope(), null, processInstanceId, flowElements);
        ProcessDefinitionKey processDefinitionKey = processInstance.getProcessDefinitionKey();
        processDefinitionKeyThreadLocal.set(processDefinitionKey);

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            createProcessInstanceProcessingContext(processInstance, instanceResult);

        doWhileCatching(
            processInstanceProcessingContext,
            processDefinitionKey,
            processInstance.getScope(),
            () ->
                scopeProcessor.processContinue(
                    processInstanceProcessingContext,
                    processInstance.getScope(),
                    trigger,
                    trigger.getElementInstanceIdPath()));
      }
    } else {
      log.warn("Process instanceToContinue not found for key: {}", processInstanceId);
    }
  }

  private void handleTerminate(AbortTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId1 = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId1);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {
        UUID processInstanceId = processInstanceDTO.getProcessInstanceId();
        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
        enrichScope(processInstance.getScope(), null, processInstanceId, flowElements);
        ProcessDefinitionKey processDefinitionKey = processInstance.getProcessDefinitionKey();
        processDefinitionKeyThreadLocal.set(processDefinitionKey);

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            createProcessInstanceProcessingContext(processInstance, instanceResult);

        doWhileCatching(
            processInstanceProcessingContext,
            processDefinitionKey,
            processInstance.getScope(),
            () ->
                scopeProcessor.processAbort(
                    processInstanceProcessingContext, processInstance.getScope(), trigger));
      }
    }
  }

  private void processResultAndForward(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      ProcessDefinitionKey processDefinitionKey,
      Scope scope) {

    EventSignal eventSignal = scope.getDirectInstanceResult().pollBubbleUpEvent();
    List<EventSignalDTO> eventSignalList = new ArrayList<>();
    while (eventSignal != null) {
      if (eventSignal instanceof ErrorEventSignal) {
        AbortTriggerDTO trigger =
            new AbortTriggerDTO(
                processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
                Collections.emptyList());
        scopeProcessor.processAbort(processInstanceProcessingContext, scope, trigger);
      }
      eventSignalList.add(dtoMapper.map(eventSignal));
      eventSignal = scope.getDirectInstanceResult().pollBubbleUpEvent();
    }

    ScopeDTO scopeDTO = scopeToDTO(scope);
    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder().scope(scopeDTO).build();

    scope.persistVariables();

    InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();

    if (scope.isStateChanged()
        || !scope.getVariableScope().getDirtyVariables().isEmpty()
        || processInstance.getIncidentInfo() != null) {
      if (scope.getState() == ExecutionState.COMPLETED) {
        processingStatistics.increaseProcessInstancesFinished();
      }

      instanceResult.addInstanceUpdate(processInstanceToUpdate(processInstance, scope));
    }

    if (processInstance.getScope().getState().isDone() && eventSignalList.isEmpty()) {
      continueParentInstance(instanceResult, processInstance, scope);
    } else if (!eventSignalList.isEmpty()) {
      throwEventToParentInstance(instanceResult, processInstance, scope, eventSignalList);
    }

    forwarder.forward(context, instanceResult, processDefinitionKey, processInstanceDTO, scope);

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
      InstanceResult instanceResult, ProcessInstance processInstance, Scope scope) {
    if (processInstance.getParentProcessInstanceId() != null) {
      VariablesDTO variables;

      if (processInstance.isPropagateAllToParent()) {
        variables = VariablesDTO.ofJsonMap(scope.retrieveAndFlattenAllVariables());
      } else {
        VariableScope outputVariables = new VariableScope(scope, variablesStore);
        ioMappingProcessor.addVariables(outputVariables, processInstance.getOutputMappings());
        variables = VariablesDTO.ofJsonMap(outputVariables.getVariables());
      }

      instanceResult.addContinuation(
          new ContinueFlowElementTriggerDTO(
              processInstance.getParentProcessInstanceId(),
              processInstance.getParentElementInstancePath(),
              null,
              variables));
    }
  }

  private void throwEventToParentInstance(
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      Scope scope,
      List<EventSignalDTO> eventSignalList) {
    if (processInstance.getParentProcessInstanceId() != null) {
      VariablesDTO variables;

      if (processInstance.isPropagateAllToParent()) {
        variables = VariablesDTO.ofJsonMap(scope.retrieveAndFlattenAllVariables());
      } else {
        VariableScope outputVariables = new VariableScope(scope, variablesStore);
        ioMappingProcessor.addVariables(outputVariables, processInstance.getOutputMappings());
        variables = VariablesDTO.ofJsonMap(outputVariables.getVariables());
      }
      instanceResult.addEventSignals(
          new EventSignalTriggerDTO(
              processInstance.getParentProcessInstanceId(),
              processInstance.getParentElementInstancePath(),
              variables,
              eventSignalList));
    }
  }

  private void enrichScope(
      Scope scope, Scope parentScope, UUID processInstanceId, FlowElements flowElements) {
    scope.setParentScope(parentScope);
    scope.setProcessInstanceId(processInstanceId);
    VariableScope variableScope = new VariableScope(scope, variablesStore);
    scope.setVariableScope(variableScope);
    scope.setProcessInstanceMapper(instanceMapper);
    scope.setVariableStore(variablesStore);
    scope.setFlowNodeInstanceStore(flowNodeInstanceStore);
    scope.setFlowElements(flowElements);
    scope.setFlowNodeInstances(new FlowNodeInstances(scope, flowNodeInstanceStore));
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

  private void doWhileCatching(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      ProcessDefinitionKey processDefinitionKey,
      Scope scope,
      Callable<Void> callable) {

    try {
      callable.call();
    } catch (ProcessInstanceException processInstanceException) {
      StackTraceElement[] stackTrace = processInstanceException.getStackTrace();
      String[] stackTraceStrings =
          Arrays.stream(stackTrace).map(StackTraceElement::toString).toArray(String[]::new);
      processInstanceProcessingContext
          .getProcessInstance()
          .setIncidentInfo(
              new IncidentInfo(
                  processInstanceException.getFlowNodeInstance(),
                  processInstanceException.getMessage(),
                  stackTraceStrings));
    } catch (Exception t) {
      StackTraceElement[] stackTrace = t.getStackTrace();
      String[] stackTraceStrings =
          Arrays.stream(stackTrace).map(StackTraceElement::toString).toArray(String[]::new);
      processInstanceProcessingContext
          .getProcessInstance()
          .setIncidentInfo(new IncidentInfo(null, t.getMessage(), stackTraceStrings));
    } finally {
      processResultAndForward(processInstanceProcessingContext, processDefinitionKey, scope);
    }
  }
}
