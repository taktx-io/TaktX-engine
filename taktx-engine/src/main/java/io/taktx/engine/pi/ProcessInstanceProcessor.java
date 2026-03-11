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
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithScope;
import io.taktx.engine.pi.processor.IoMappingProcessor;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.topicmanagement.DynamicTopicManager;
import io.taktx.security.AuthorizationTokenException;
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
  private final EngineAuthorizationService engineAuthorizationService;
  private final Map<ProcessDefinitionKey, FlowElements> flowElementsCache = new HashMap<>();

  private ReadOnlyKeyValueStore<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>>
      definitionsStore;
  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private KeyValueStore<VariableKeyDTO, JsonNode> variablesStore;
  private ProcessorContext<Object, Object> context;
  private final ThreadLocal<VariableScope> variableScopeThreadLocal = new ThreadLocal<>();
  private final ThreadLocal<ProcessInstance> processInstanceThreadLocal = new ThreadLocal<>();
  private final ThreadLocal<ProcessInstanceProcessingContext>
      processInstanceProcessingContextThreadLocal = new ThreadLocal<>();
  private final ThreadLocal<ProcessDefinitionKey> processDefinitionKeyThreadLocal =
      new ThreadLocal<>();
  private final ThreadLocal<String> auditIdThreadLocal = new ThreadLocal<>();

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

    // ── Authorization ─────────────────────────────────────────────────────────
    String auditId;
    try {
      auditId = engineAuthorizationService.authorize(triggerRecord.headers(), trigger);
    } catch (AuthorizationTokenException e) {
      log.error("⛔ Command rejected — authorization failed: {}", e.getMessage());
      return;
    }
    auditIdThreadLocal.set(auditId);

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
        case EventSignalTriggerDTO eventSignalTrigger -> {
          processingStatistics.recordProcessInstanceLatency(
              kafkaTimestamp, eventSignalTrigger.getClass().getSimpleName());
          handleEvent(eventSignalTrigger);
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
      handleIncident(t);
      log.error("Internal error occurred for", t);
    } finally {
      processDefinitionKeyThreadLocal.remove();
      processInstanceThreadLocal.remove();
      variableScopeThreadLocal.remove();
      processInstanceProcessingContextThreadLocal.remove();
      auditIdThreadLocal.remove();
    }
  }

  private void handleIncident(Throwable t) {
    ProcessInstance processInstance = processInstanceThreadLocal.get();
    ProcessDefinitionKey processDefinitionKey = processDefinitionKeyThreadLocal.get();
    VariableScope variableScope = variableScopeThreadLocal.get();
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
      processResultAndForward(
          processInstanceProcessingContext, processDefinitionKey, scope, variableScope);
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
            null, processInstanceId, null, flowElements, instanceMapper, flowNodeInstanceStore);
    VariableScope variableScope = VariableScope.empty(processInstanceId, variablesStore);
    variableScope.merge(startCommand.getVariables());
    variableScopeThreadLocal.set(variableScope);

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

    instanceResult.addInstanceUpdate(
        startProcessInstanceToUpdate(processInstance, scope, variableScope));

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
                VariablesDTO.empty(),
                processInstanceProcessingContext,
                scope,
                variableScope));
  }

  private ProcessInstanceProcessingContext createProcessInstanceProcessingContext(
      ProcessInstance processInstance, InstanceResult instanceResult) {
    ProcessInstanceProcessingContext ctx =
        ProcessInstanceProcessingContext.builder()
            .processInstance(processInstance)
            .processingStatistics(processingStatistics)
            .instanceResult(instanceResult)
            .flowNodeInstanceStore(flowNodeInstanceStore)
            .topicManager(topicManager)
            .build();
    ctx.setAuditId(auditIdThreadLocal.get());
    return ctx;
  }

  private ProcessDefinitionDTO getProcessDefinitionDTO(ProcessDefinitionKey processDefinitionKey) {
    return definitionsCache.computeIfAbsent(
        processDefinitionKey, key -> definitionsStore.get(key).value());
  }

  private InstanceUpdate startProcessInstanceToUpdate(
      ProcessInstance processInstance, Scope scope, VariableScope variableScope) {

    ScopeDTO scopeDTO = scopeToDTO(scope);
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance, scope.getFlowElements()).toBuilder()
            .scope(scopeDTO)
            .build();

    VariablesDTO variables = variableScope.scopeToDTO();

    return new InstanceUpdate(
        processInstance.getProcessInstanceId(),
        new ProcessInstanceUpdateDTO(processInstanceDTO, variables, clock.millis(), null));
  }

  private InstanceUpdate processInstanceToUpdate(
      ProcessInstance processInstance, Scope scope, VariableScope variableScope) {

    ScopeDTO scopeDTO = scopeToDTO(scope);
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance, scope.getFlowElements()).toBuilder()
            .scope(scopeDTO)
            .build();

    VariablesDTO variables = variableScope.scopeToDTO();

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
    log.info(
        "Handling SetVariables for processInstanceId: {} {}",
        trigger.getProcessInstanceId(),
        trigger);
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
      enrichScope(processInstance.getScope(), processInstanceId, flowElements);
      ProcessDefinitionKey processDefinitionKey = processInstance.getProcessDefinitionKey();
      variableScopeThreadLocal.set(VariableScope.empty(processInstanceId, variablesStore));
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
                  processInstance.getScope(),
                  variableScopeThreadLocal.get()));
    }
  }

  public void handleStartFlowElement(StartFlowElementTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
      enrichScope(processInstance.getScope(), processInstanceId, flowElements);
      ProcessDefinitionKey processDefinitionKey = processInstance.getProcessDefinitionKey();
      processDefinitionKeyThreadLocal.set(processDefinitionKey);
      variableScopeThreadLocal.set(VariableScope.empty(processInstanceId, variablesStore));
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
                  processInstance.getScope(),
                  variableScopeThreadLocal.get()));
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
        enrichScope(processInstance.getScope(), processInstanceId, flowElements);
        ProcessDefinitionKey processDefinitionKey = processInstance.getProcessDefinitionKey();
        processDefinitionKeyThreadLocal.set(processDefinitionKey);

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            createProcessInstanceProcessingContext(processInstance, instanceResult);
        variableScopeThreadLocal.set(VariableScope.empty(processInstanceId, variablesStore));

        doWhileCatching(
            processInstanceProcessingContext,
            processDefinitionKey,
            processInstance.getScope(),
            () ->
                scopeProcessor.processContinue(
                    processInstanceProcessingContext,
                    processInstance.getScope(),
                    variableScopeThreadLocal.get(),
                    trigger,
                    trigger.getElementInstanceIdPath()));
      }
    } else {
      log.warn("Process instanceToContinue not found for key: {}", processInstanceId);
    }
  }

  public void handleEvent(EventSignalTriggerDTO trigger) {

    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {

        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
        enrichScope(processInstance.getScope(), processInstanceId, flowElements);
        ProcessDefinitionKey processDefinitionKey = processInstance.getProcessDefinitionKey();
        processDefinitionKeyThreadLocal.set(processDefinitionKey);

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            createProcessInstanceProcessingContext(processInstance, instanceResult);
        variableScopeThreadLocal.set(VariableScope.empty(processInstanceId, variablesStore));

        doWhileCatching(
            processInstanceProcessingContext,
            processDefinitionKey,
            processInstance.getScope(),
            () -> {
              EventSignal eventSignal = dtoMapper.map(trigger.getEventSignal());
              scopeProcessor.processEvent(
                  processInstanceProcessingContext,
                  processInstance.getScope(),
                  variableScopeThreadLocal.get(),
                  trigger,
                  eventSignal);
              return null;
            });
      }
    } else {
      log.warn("Process instanceToContinue not found for key: {}", processInstanceId);
    }
  }

  private void handleTerminate(AbortTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {
        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);
        enrichScope(processInstance.getScope(), processInstanceId, flowElements);
        ProcessDefinitionKey processDefinitionKey = processInstance.getProcessDefinitionKey();
        processDefinitionKeyThreadLocal.set(processDefinitionKey);
        variableScopeThreadLocal.set(VariableScope.empty(processInstanceId, variablesStore));

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            createProcessInstanceProcessingContext(processInstance, instanceResult);

        doWhileCatching(
            processInstanceProcessingContext,
            processDefinitionKey,
            processInstance.getScope(),
            () ->
                scopeProcessor.processAbort(
                    processInstanceProcessingContext,
                    processInstance.getScope(),
                    variableScopeThreadLocal.get(),
                    trigger));
      }
    }
  }

  private void processResultAndForward(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      ProcessDefinitionKey processDefinitionKey,
      Scope scope,
      VariableScope variableScope) {

    ProcessInstance processInstance = processInstanceProcessingContext.getProcessInstance();

    EventSignal eventSignal = scope.getDirectInstanceResult().pollBubbleUpEvent();
    List<EventSignalDTO> eventSignalList = new ArrayList<>();
    while (eventSignal != null) {
      if (eventSignal instanceof ErrorEventSignal errorEventSignal
          && processInstance.getParentProcessInstanceId() == null) {
        // Unhandled error event - create an incident
        // This is BPMN 2.0 compliant: when error handling fails, it's an exceptional condition
        // requiring human intervention (similar to Camunda/Zeebe behavior)
        processInstance.setIncidentInfo(
            new IncidentInfo(
                errorEventSignal.getCurrentInstance(),
                "Unhandled error event: "
                    + errorEventSignal.getCode()
                    + " - "
                    + errorEventSignal.getMessage(),
                new String[] {
                  "Error code: " + errorEventSignal.getCode(),
                  "Error message: " + errorEventSignal.getMessage(),
                  "Source element: " + errorEventSignal.getCurrentInstance().getFlowNode().getId()
                }));
        // Don't abort - leave process in incident state for potential resolution
      } else if (eventSignal instanceof EscalationEventSignal escalationEventSignal) {
        // Unhandled escalation event - this is BPMN 2.0 compliant
        // Unlike errors, escalations can be unhandled without creating incidents
        // They are forwarded to parent process (if any) via eventSignalList
        // If no parent exists, the escalation is logged but process continues normally
        log.warn(
            "Unhandled escalation event: {} - forwarding to parent or discarding",
            escalationEventSignal.getCode());
      }
      // All event signals are added to the list for potential forwarding to parent
      EventSignalDTO map = dtoMapper.map(eventSignal);
      map.setElementInstanceIdPath(
          eventSignal.getCurrentInstance() != null
              ? eventSignal.getCurrentInstance().createKeyPath()
              : List.of());
      eventSignalList.add(map);
      eventSignal = scope.getDirectInstanceResult().pollBubbleUpEvent();
    }

    variableScope.persistTree(processInstance.getProcessInstanceId(), variablesStore);

    InstanceResult instanceResult = processInstanceProcessingContext.getInstanceResult();

    if (scope.isStateChanged()
        || !variableScope.getDirtyVariables().isEmpty()
        || processInstance.getIncidentInfo() != null) {
      if (scope.getState() == ExecutionState.COMPLETED) {
        processingStatistics.increaseProcessInstancesFinished();
      }

      instanceResult.addInstanceUpdate(
          processInstanceToUpdate(processInstance, scope, variableScope));
    }

    if (processInstance.getScope().getState().isDone() && eventSignalList.isEmpty()) {
      continueParentInstance(instanceResult, processInstance, variableScope);
    } else if (!eventSignalList.isEmpty()) {
      for (EventSignalDTO eventSignalDTO : eventSignalList) {
        log.info(
            "ProcessInstance {} throwing event to parent: {}",
            processInstance.getProcessInstanceId(),
            eventSignalDTO);
        throwEventToParentInstance(instanceResult, processInstance, variableScope, eventSignalDTO);
      }
    }

    forwarder.forward(
        context, instanceResult, processDefinitionKey, processInstance, auditIdThreadLocal.get());

    ScopeDTO scopeDTO = scopeToDTO(scope);
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance, scope.getFlowElements()).toBuilder()
            .scope(scopeDTO)
            .build();

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
      InstanceResult instanceResult, ProcessInstance processInstance, VariableScope variableScope) {
    if (processInstance.getParentProcessInstanceId() != null) {
      VariablesDTO variables;

      if (processInstance.isPropagateAllToParent()) {
        variables = VariablesDTO.ofJsonMap(variableScope.retrieveAndFlattenAllVariables());
      } else {
        VariableScope outputVariables =
            VariableScope.empty(processInstance.getProcessInstanceId(), variablesStore);
        ioMappingProcessor.addVariables(
            variableScope, outputVariables, processInstance.getOutputMappings());
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
      VariableScope variableScope,
      EventSignalDTO eventSignal) {
    if (processInstance.getParentProcessInstanceId() != null) {
      VariablesDTO variables;
      if (processInstance.isPropagateAllToParent()) {
        variables = VariablesDTO.ofJsonMap(variableScope.retrieveAndFlattenAllVariables());
      } else {
        VariableScope outputVariables =
            VariableScope.empty(processInstance.getProcessInstanceId(), variablesStore);
        ioMappingProcessor.addVariables(
            outputVariables, variableScope.getParentScope(), processInstance.getOutputMappings());
        variables = VariablesDTO.ofJsonMap(outputVariables.getVariables());
      }
      eventSignal.getVariables().getVariables().forEach(variables::put);
      eventSignal.setVariables(variables);

      instanceResult.addEventSignals(
          new EventSignalTriggerDTO(processInstance.getParentProcessInstanceId(), eventSignal));
    }
  }

  private void enrichScope(Scope scope, UUID processInstanceId, FlowElements flowElements) {
    scope.setParentScope(null);
    scope.setProcessInstanceId(processInstanceId);
    scope.setProcessInstanceMapper(instanceMapper);
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
        instanceMapper.map(scope.getSubscriptions(), scope.getFlowElements()));
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
      processResultAndForward(
          processInstanceProcessingContext,
          processDefinitionKey,
          scope,
          variableScopeThreadLocal.get());
    }
  }
}
