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
import io.taktx.dto.VariableKeyDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithFlowNodeInstances;
import io.taktx.engine.pi.processor.IoMappingProcessor;
import io.taktx.engine.topicmanagement.DynamicTopicManager;
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
  private final DefinitionsCache definitionsCache;
  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final Forwarder forwarder;
  private final IoMappingProcessor ioMappingProcessor;
  private final TaktConfiguration taktConfiguration;
  private final FlowNodeInstancesProcessor flowNodeInstancesProcessor;
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
    long processingStartTime = clock.millis();
    long kafkaTimestamp = triggerRecord.timestamp();

    try {
      switch (trigger) {
        case StartCommandDTO startCommand -> {
          // Record end-to-end latency using Kafka timestamp
          processingStatistics.recordProcessInstanceLatency(
              kafkaTimestamp, trigger.getClass().getSimpleName());

          long caseStartTime = clock.millis();
          processStartCommandRecord(triggerRecord.key(), startCommand);
          logProcessingStageIfHighLatency(
              kafkaTimestamp,
              caseStartTime,
              "StartCommand",
              startCommand.getProcessDefinitionKey().toString());
        }
        case StartFlowElementTriggerDTO starteFlowElementTrigger -> {
          // Record end-to-end latency using Kafka timestamp
          processingStatistics.recordProcessInstanceLatency(
              kafkaTimestamp, trigger.getClass().getSimpleName());

          long caseStartTime = clock.millis();
          handleStartFlowElement(starteFlowElementTrigger);
          logProcessingStageIfHighLatency(
              kafkaTimestamp,
              caseStartTime,
              "StartFlowElement",
              starteFlowElementTrigger.getElementId());
        }
        case ContinueFlowElementTriggerDTO continueFlowElementTrigger2 -> {
          processingStatistics.recordExternalTaskResponseLatency(
              kafkaTimestamp, continueFlowElementTrigger2.getClass().getSimpleName());
          long caseStartTime = clock.millis();
          handleContinue(continueFlowElementTrigger2);
          logProcessingStageIfHighLatency(
              kafkaTimestamp,
              caseStartTime,
              "ContinueFlowElement",
              continueFlowElementTrigger2.getProcessInstanceId().toString());
        }
        case TerminateTriggerDTO terminateTrigger -> {
          // Record end-to-end latency using Kafka timestamp
          processingStatistics.recordProcessInstanceLatency(
              kafkaTimestamp, trigger.getClass().getSimpleName());

          long caseStartTime = clock.millis();
          handleTerminate(flowNodeInstanceStore, terminateTrigger);
          logProcessingStageIfHighLatency(
              kafkaTimestamp,
              caseStartTime,
              "Terminate",
              terminateTrigger.getProcessInstanceId().toString());
        }
        default ->
            throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
      }

      long now = clock.millis();
      // Log total processing time if high latency detected
      long totalProcessingTime = now - processingStartTime;
      long totalLatency = now - kafkaTimestamp;
      if (totalLatency > 200) { // Log if >200ms total latency
        log.warn(
            "PROCESSING BREAKDOWN - Total: {}ms | Processing: {}ms | Queue: {}ms | Type: {}",
            totalLatency,
            totalProcessingTime,
            (totalLatency - totalProcessingTime),
            trigger.getClass().getSimpleName());
      }

    } catch (ProcessInstanceException e) {
      handleExceptional(flowNodeInstanceStore, e);
    } catch (Throwable t) { // NOSONAR
      log.error("Internal error occurred for", t);
    }
  }

  private void logProcessingStageIfHighLatency(
      long kafkaTimestamp, long stageStartTime, String stageName, String identifier) {
    long now = clock.millis();
    long stageLatency = now - stageStartTime;
    long totalLatency = now - kafkaTimestamp;

    if (totalLatency > 200) { // Only log for high-latency events
      log.warn(
          "STAGE TIMING - {}: {}ms | Total: {}ms | ID: {}",
          stageName,
          stageLatency,
          totalLatency,
          identifier);
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

    FlowNodeInstances flowNodeInstances = new FlowNodeInstances();

    VariableScope processInstanceVariables =
        new VariableScope(variablesStore, processInstanceId, null, null);

    processInstanceVariables.merge(startCommand.getVariables());

    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(processDefinition);

    Set<IoVariableMapping> ioVariableMappings = dtoMapper.map(startCommand.getOutputMappings());

    ProcessInstance processInstance =
        new ProcessInstance(
            processInstanceId,
            startCommand.getParentProcessInstanceId(),
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
        .topicManager(topicManager)
        .build();
  }

  private ProcessDefinitionDTO getProcessDefinitionDTO(ProcessDefinitionKey processDefinitionKey) {
    long cacheStartTime = clock.millis();

    ProcessDefinitionDTO result =
        definitionsCache.computeIfAbsent(
            processDefinitionKey,
            key -> {
              long storeStartTime = clock.millis();
              ProcessDefinitionDTO dto = definitionsStore.get(key).value();
              long storeLatency = clock.millis() - storeStartTime;

              // Log slow state store access
              if (storeLatency > 50) {
                log.warn(
                    "SLOW STATE STORE ACCESS - ProcessDefinition fetch: {}ms for key: {}",
                    storeLatency,
                    processDefinitionKey);
              }

              return dto;
            });

    long cacheLatency = clock.millis() - cacheStartTime;

    // Log slow cache operations
    if (cacheLatency > 50) {
      log.warn(
          "SLOW CACHE ACCESS - ProcessDefinition cache: {}ms for key: {}",
          cacheLatency,
          processDefinitionKey);
    }

    return result;
  }

  private void handleExceptional(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      ProcessInstanceException e) {
    FlowNodeInstance<?> flowNodeInstance = e.getFlowNodeInstance();
    flowNodeInstance.terminate();
    ProcessInstance processInstance = e.getProcessInstance();
    log.warn(
        "Takt exception occurred for processinstance {}, {}, {}: {}",
        processInstance.getProcessInstanceId(),
        flowNodeInstance.getFlowNode().getId(),
        flowNodeInstance.getElementInstanceId(),
        e.getMessage());
    handleTerminate(
        flowNodeInstanceStore,
        new TerminateTriggerDTO(processInstance.getProcessInstanceId(), List.of()));
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
        processInstance.getProcessInstanceId(),
        new ProcessInstanceUpdateDTO(processInstanceDTO, variables, processTime));
  }

  public void handleStartFlowElement(StartFlowElementTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      VariableScope processInstanceVariables =
          new VariableScope(variablesStore, processInstanceId, null, null);
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
    UUID processInstanceId = trigger.getProcessInstanceId();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceId);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {

        VariableScope processInstanceVariables =
            new VariableScope(variablesStore, processInstanceId, null, null);

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
      log.warn("Process instanceToContinue not found for key: {}", processInstanceId);
    }
  }

  private void mergeVariablesInScope(
      VariableScope processInstanceVariables,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables) {
    VariableScope targetScope = processInstanceVariables;
    if (elementInstanceIdPath != null) {
      // merge into the nearest parent scope
      for (int i = 0; i < elementInstanceIdPath.size() - 1; i++) {
        Long elementInstanceId = elementInstanceIdPath.get(i);
        targetScope = targetScope.selectFlowNodeInstancesScope(elementInstanceId);
      }
    }
    targetScope.merge(variables);
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

  private void handleTerminate(
      KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore,
      TerminateTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceId());
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {
        VariableScope processInstanceVariables =
            new VariableScope(
                variablesStore, processInstanceDTO.getProcessInstanceId(), null, null);
        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);

        ProcessInstanceProcessingContext processInstanceProcessingContext =
            ProcessInstanceProcessingContext.builder()
                .flowNodeInstanceStore(flowNodeInstanceStore)
                .processInstance(processInstance)
                .processingStatistics(processingStatistics)
                .instanceResult(instanceResult)
                .topicManager(topicManager)
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

    VariablesDTO currentVariablesDTO = processInstanceVariables.scopeToDTO();
    if (flowNodeInstances.isStateChanged() || !currentVariablesDTO.getVariables().isEmpty()) {
      if (flowNodeInstances.getState() == ProcessInstanceState.COMPLETED) {
        processingStatistics.increaseProcessInstancesFinished();
      }

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
      processInstanceStore.put(processInstance.getProcessInstanceId(), processInstanceDTO);
      storeFlowNodeInstances(
          processInstance.getProcessInstanceId(),
          processInstance.getFlowNodeInstances(),
          flowElements);
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
            new VariableScope(
                processInstanceVariables.getVariableStore(),
                processInstance.getProcessInstanceId(),
                null,
                null);
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
      UUID processInstanceId, FlowNodeInstances flowNodeInstances, FlowElements flowElements) {
    for (FlowNodeInstance<?> fLowNodeInstance : flowNodeInstances.getInstances().values()) {
      storeFlowNodeInstance(processInstanceId, fLowNodeInstance, flowElements);
    }
  }

  private void storeFlowNodeInstance(
      UUID processInstanceId, FlowNodeInstance<?> fLowNodeInstance, FlowElements flowElements) {
    if (fLowNodeInstance.isDirty()) {
      FlowNodeInstanceDTO flowNodeInstanceDTO = instanceMapper.map(fLowNodeInstance, flowElements);
      FlowNodeInstanceKeyDTO key =
          new FlowNodeInstanceKeyDTO(processInstanceId, fLowNodeInstance.createKeyPath());
      flowNodeInstanceStore.put(key, flowNodeInstanceDTO);
    }

    if (fLowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances) {
      storeFlowNodeInstances(
          processInstanceId, withFlowNodeInstances.getFlowNodeInstances(), flowElements);
    }
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
