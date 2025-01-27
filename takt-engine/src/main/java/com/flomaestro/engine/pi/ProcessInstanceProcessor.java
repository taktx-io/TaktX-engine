package com.flomaestro.engine.pi;

import static com.flomaestro.takt.dto.v_1_0_0.Constants.MAX_UUID;
import static com.flomaestro.takt.dto.v_1_0_0.Constants.MIN_UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.engine.pd.Stores;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.IoVariableMapping;
import com.flomaestro.engine.pi.model.AbstractVariableScope;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.FlowNodeInstancesVariables;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.ProcessInstanceVariables;
import com.flomaestro.engine.pi.model.WithFlowNodeInstances;
import com.flomaestro.engine.pi.processor.IoMappingProcessor;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowElementDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstancesDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessingStatisticsDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import com.flomaestro.takt.util.TaktUUIDSerde;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.api.RecordMetadata;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

@Slf4j
public class ProcessInstanceProcessor
    implements Processor<UUID, ProcessInstanceTriggerDTO, Object, Object> {

  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final Forwarder forwarder;
  private final IoMappingProcessor ioMappingProcessor;
  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final FlowNodeInstancesProcessor flowNodeInstancesProcessor;
  private final Clock clock;
  private final DtoMapper dtoMapper;
  private ReadOnlyKeyValueStore<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>>
      definitionsStore;
  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore;
  private KeyValueStore<VariableKeyDTO, JsonNode> variablesStore;
  private ProcessorContext<Object, Object> context;
  private final Map<ProcessDefinitionKey, FlowElements> flowElementsCache = new HashMap<>();
  private final Map<ProcessDefinitionKey, ProcessDefinitionDTO> definitionsCache = new HashMap<>();
  private final Map<String, ProcessingStatistics> stats = new HashMap<>();

  public ProcessInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      DtoMapper dtoMapper,
      DefinitionMapper definitionMapper,
      ProcessInstanceMapper instanceMapper,
      Forwarder forwarder,
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper,
      FlowNodeInstancesProcessor flowNodeInstancesProcessor,
      Clock clock) {
    this.ioMappingProcessor = ioMappingProcessor;
    this.definitionMapper = definitionMapper;
    this.dtoMapper = dtoMapper;
    this.instanceMapper = instanceMapper;
    this.forwarder = forwarder;
    this.tenantNamespaceNameWrapper = tenantNamespaceNameWrapper;
    this.flowNodeInstancesProcessor = flowNodeInstancesProcessor;
    this.clock = clock;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.definitionsStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(
                Stores.GLOBAL_PROCESS_DEFINITION.getStorename()));
    this.variablesStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.VARIABLES.getStorename()));
    this.processInstanceStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()));
    this.flowNodeInstanceStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.FLOW_NODE_INSTANCE.getStorename()));

    context.schedule(
        Duration.ofMillis(10000),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp ->
            stats.forEach(
                (statsKey, statistics) -> {
                  ProcessingStatisticsDTO statisticsDto = statistics.toDTO();
                  statistics.reset();
                  context.forward(new Record<>(statsKey, statisticsDto, timestamp));
                }));
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTriggerDTO> triggerRecord) {
    ProcessingStatistics processingStatistics;
    int partition = -1;
    String topic = "unknowntopic";
    Optional<RecordMetadata> optRecordMetadata = context.recordMetadata();
    if (optRecordMetadata.isPresent()) {
      RecordMetadata recordMetadata = optRecordMetadata.get();
      partition = recordMetadata.partition();
      topic = recordMetadata.topic();
    } else {
      log.error("No record metadata available, should not occur");
    }
    String statsKey = topic + "-" + partition;
    processingStatistics = stats.computeIfAbsent(statsKey, k -> new ProcessingStatistics());
    processingStatistics.increaseRequestsReceived();
    processingStatistics.addRequestToProcessingLatency(clock.millis() - triggerRecord.timestamp());

    ProcessInstanceTriggerDTO trigger = triggerRecord.value();

    try {
      switch (trigger) {
        case StartCommandDTO startCommand ->
            processStartCommandRecord(triggerRecord.key(), startCommand, processingStatistics);
        case ContinueFlowElementTriggerDTO continueFlowElementTrigger2 ->
            handleContinue(continueFlowElementTrigger2, processingStatistics);
        case TerminateTriggerDTO terminateTrigger ->
            handleTerminate(flowNodeInstanceStore, terminateTrigger, processingStatistics);
        default ->
            throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
      }
    } catch (ProcessInstanceException e) {
      handleExceptional(flowNodeInstanceStore, e, processingStatistics);
    } catch (Throwable t) {
      log.error("Internal error occurred for", t);
    }
  }

  private void processStartCommandRecord(
      UUID processInstanceKey,
      StartCommandDTO startCommand,
      ProcessingStatistics processingStatistics) {
    log.info(
        "Start new process instance: {} definition: {}",
        processInstanceKey,
        startCommand.getProcessDefinitionKey());

    processingStatistics.increaseProcessInstancesStarted();

    ProcessDefinitionDTO processDefinition =
        getProcessDefinitionDTO(startCommand.getProcessDefinitionKey());

    String startNodeId = startCommand.getElementIdPath().getFirst();
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

    ProcessInstanceVariables processInstanceVariables =
        new ProcessInstanceVariables(variablesStore, processInstanceKey);

    processInstanceVariables.merge(startCommand.getVariables());

    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(processDefinition);

    Set<IoVariableMapping> ioVariableMappings = dtoMapper.map(startCommand.getOutputMappings());
    ProcessInstance processInstance =
        new ProcessInstance(
            processInstanceKey,
            startCommand.getParentProcessInstanceKey(),
            startCommand.getParentElementIdPath(),
            startCommand.getParentElementInstancePath(),
            processDefinitionKey,
            flowNodeInstances,
            startCommand.isPropagateAllToParent(),
            ioVariableMappings);

    InstanceResult instanceResult = InstanceResult.empty();

    instanceResult.addInstanceUpdate(
        processInstanceToUpdate(
            processInstance, flowNodeInstances, processInstanceVariables, clock.millis()));

    FlowElements flowElements =
        definitionMapper.getFlowElements(processDefinition.getDefinitions());

    FlowNodeInstancesVariables flowNodeInstancesScope =
        processInstanceVariables.selectFlowNodeInstancesScope(
            flowNodeInstances.getFlowNodeInstancesId());

    flowNodeInstancesProcessor.processStart(
        flowNodeInstanceStore,
        instanceResult,
        startEventId,
        null,
        flowElements,
        processInstance,
        flowNodeInstancesScope,
        flowNodeInstances,
        processingStatistics);

    processResultAndForward(
        instanceResult,
        processDefinitionKey,
        processInstance,
        flowNodeInstances,
        processInstanceVariables,
        processingStatistics);
  }

  private ProcessDefinitionDTO getProcessDefinitionDTO(ProcessDefinitionKey processDefinitionKey) {
    return definitionsCache.computeIfAbsent(
        processDefinitionKey, key -> definitionsStore.get(key).value());
  }

  private void handleExceptional(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      ProcessInstanceException e,
      ProcessingStatistics processingStatistics) {
    FlowNodeInstance<?> flowNodeInstance = e.getFlowNodeInstance();
    flowNodeInstance.terminate();
    ProcessInstance processInstance = e.getProcessInstance();
    log.warn(
        "Takt exception occurred for processinstance {}, {}, {}",
        processInstance.getProcessInstanceKey(),
        flowNodeInstance.getFlowNode().getId(),
        flowNodeInstance.getElementInstanceId());
    handleTerminate(
        flowNodeInstanceStore,
        new TerminateTriggerDTO(processInstance.getProcessInstanceKey(), List.of()),
        processingStatistics);
  }

  private ProcessInstanceUpdateDTO processInstanceToUpdate(
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      AbstractVariableScope variables,
      long processTime) {

    VariablesDTO variablesDTO = variables.scopeToDTO();

    FlowNodeInstancesDTO flowNodeInstancesDTO = flowNodeInstancesToDTO(flowNodeInstances);
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder()
            .flowNodeInstances(flowNodeInstancesDTO)
            .build();

    return new ProcessInstanceUpdateDTO(processInstanceDTO, variablesDTO, processTime);
  }

  public void handleContinue(
      ContinueFlowElementTriggerDTO trigger, ProcessingStatistics processingStatistics) {
    InstanceResult instanceResult = InstanceResult.empty();
    UUID processInstanceKey = trigger.getProcessInstanceKey();
    ProcessInstanceDTO processInstanceDTO = processInstanceStore.get(processInstanceKey);
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {
        FlowNodeInstances flowNodeInstances =
            getFlowNodeInstances(processInstanceDTO.getFlowNodeInstances());

        ProcessInstanceVariables processInstanceVariables =
            new ProcessInstanceVariables(variablesStore, processInstanceKey);
        FlowNodeInstancesVariables flowNodeInstancesVariables =
            processInstanceVariables.selectFlowNodeInstancesScope(
                flowNodeInstances.getFlowNodeInstancesId());

        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);

        flowNodeInstancesProcessor.processContinue(
            flowNodeInstanceStore,
            instanceResult,
            0,
            trigger,
            flowElements,
            processInstance,
            flowNodeInstancesVariables,
            processInstance.getFlowNodeInstances(),
            processingStatistics);

        processResultAndForward(
            instanceResult,
            processInstance.getProcessDefinitionKey(),
            processInstance,
            processInstance.getFlowNodeInstances(),
            processInstanceVariables,
            processingStatistics);
      }
    } else {
      log.warn("Process instance not found for key: {}", processInstanceKey);
    }
  }

  private static FlowNodeInstances getFlowNodeInstances(FlowNodeInstancesDTO flowNodeInstancesDto) {
    FlowNodeInstances flowNodeInstances = new FlowNodeInstances();
    flowNodeInstances.setFlowNodeInstancesId(flowNodeInstancesDto.getFlowNodeInstancesId());
    flowNodeInstances.setState(flowNodeInstancesDto.getState());
    return flowNodeInstances;
  }

  private FlowElements getFlowElements(ProcessDefinitionKey processDefinitionKey) {
    return flowElementsCache.computeIfAbsent(
        processDefinitionKey,
        key -> {
          ValueAndTimestamp<ProcessDefinitionDTO> processDefinitionDTOValueAndTimestamp =
              definitionsStore.get(key);
          ProcessDefinitionDTO processDefinitionDTO = processDefinitionDTOValueAndTimestamp.value();
          if (processDefinitionDTO != null) {
            return definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions());
          }
          return null;
        });
  }

  private void handleTerminate(
      KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore,
      TerminateTriggerDTO trigger,
      ProcessingStatistics processingStatistics) {
    InstanceResult instanceResult = InstanceResult.empty();
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {
        ProcessInstanceVariables processInstanceVariables =
            new ProcessInstanceVariables(
                variablesStore, processInstanceDTO.getProcessInstanceKey());
        FlowNodeInstances flowNodeInstances =
            getFlowNodeInstances(processInstanceDTO.getFlowNodeInstances());
        FlowNodeInstancesVariables flowNodeInstancesVariables =
            processInstanceVariables.selectFlowNodeInstancesScope(
                flowNodeInstances.getFlowNodeInstancesId());
        ProcessInstance processInstance = instanceMapper.map(processInstanceDTO, flowElements);

        flowNodeInstancesProcessor.processTerminate(
            flowNodeInstanceStore,
            instanceResult,
            trigger,
            processInstance,
            processInstance.getFlowNodeInstances(),
            flowNodeInstancesVariables,
            flowElements,
            processingStatistics);

        processResultAndForward(
            instanceResult,
            processInstance.getProcessDefinitionKey(),
            processInstance,
            processInstance.getFlowNodeInstances(),
            processInstanceVariables,
            processingStatistics);
      }
    }
  }

  private void processResultAndForward(
      InstanceResult instanceResult,
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      ProcessInstanceVariables processInstanceVariables,
      ProcessingStatistics processingStatistics) {

    FlowNodeInstancesDTO flowNodeInstancesDTO = flowNodeInstancesToDTO(flowNodeInstances);

    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder()
            .flowNodeInstances(flowNodeInstancesDTO)
            .build();

    Map<String, JsonNode> stored = processInstanceVariables.persist();
    VariablesDTO dirtyVariablesDTO = VariablesDTO.of(stored);

    if (flowNodeInstances.isStateChanged()) {
      if (flowNodeInstances.getState() == ProcessInstanceState.COMPLETED) {
        processingStatistics.increaseProcessInstancesFinished();
      }
      instanceResult.addInstanceUpdate(
          new ProcessInstanceUpdateDTO(processInstanceDTO, dirtyVariablesDTO, clock.millis()));
    }

    if (processInstance.getFlowNodeInstances().getState().isFinished()) {
      continueParentInstance(instanceResult, processInstance, processInstanceVariables);
    }

    forwarder.forward(context, instanceResult, processDefinitionKey, processInstanceDTO);

    if (processInstance.getFlowNodeInstances().getState().isFinished()) {
      purgeProcessInstance(processInstanceDTO);
    } else {
      processInstanceStore.put(processInstance.getProcessInstanceKey(), processInstanceDTO);
      storeFlowNodeInstances(processInstance.getFlowNodeInstances());
    }
  }

  private void continueParentInstance(
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      ProcessInstanceVariables processInstanceVariables) {
    if (!processInstance.getParentProcessInstanceKey().equals(Constants.NONE_UUID)) {
      VariablesDTO variables;

      if (processInstance.isPropagateAllToParent()) {
        variables = VariablesDTO.of(processInstanceVariables.retrieveAndFlattenAll());
      } else {
        ProcessInstanceVariables outputVariables =
            new ProcessInstanceVariables(
                processInstanceVariables.getVariableStore(),
                processInstance.getProcessInstanceKey());
        ioMappingProcessor.addVariables(outputVariables, processInstance.getOutputMappings());
        variables = VariablesDTO.of(outputVariables.getVariables());
      }

      instanceResult.addContinuation(
          new ContinueFlowElementTriggerDTO(
              processInstance.getParentProcessInstanceKey(),
              processInstance.getParentElementInstancePath(),
              Constants.NONE,
              variables));
    }
  }

  private static FlowNodeInstancesDTO flowNodeInstancesToDTO(FlowNodeInstances flowNodeInstances) {
    return new FlowNodeInstancesDTO(
        flowNodeInstances.getState(),
        flowNodeInstances.getFlowNodeInstancesId(),
        flowNodeInstances.getActiveCnt());
  }

  private void storeFlowNodeInstances(FlowNodeInstances flowNodeInstances) {
    for (FlowNodeInstance<?> fLowNodeInstance : flowNodeInstances.getInstances().values()) {
      storeFlowNodeInstance(flowNodeInstances, fLowNodeInstance);
    }
  }

  private void storeFlowNodeInstance(
      FlowNodeInstances flowNodeInstances, FlowNodeInstance<?> fLowNodeInstance) {
    if (fLowNodeInstance.isDirty()) {
      FlowNodeInstanceDTO flowNodeInstanceDTO = instanceMapper.map(fLowNodeInstance);

      UUID[] key =
          new UUID[] {
            flowNodeInstances.getFlowNodeInstancesId(), flowNodeInstanceDTO.getElementInstanceId()
          };
      flowNodeInstanceStore.put(key, flowNodeInstanceDTO);
    }

    if (fLowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances) {
      storeFlowNodeInstances(withFlowNodeInstances.getFlowNodeInstances());
    }
  }

  private void purgeProcessInstance(ProcessInstanceDTO processInstance) {
    UUID processInstanceKey = processInstance.getProcessInstanceKey();
    log.info("Purging finished process instance: {}", processInstanceKey);
    this.processInstanceStore.delete(processInstanceKey);
    UUID[] start =
        new UUID[] {
          processInstance.getFlowNodeInstances().getFlowNodeInstancesId(), TaktUUIDSerde.MIN_UUID
        };
    UUID[] end =
        new UUID[] {
          processInstance.getFlowNodeInstances().getFlowNodeInstancesId(), TaktUUIDSerde.MAX_UUID
        };

    try (KeyValueIterator<UUID[], FlowNodeInstanceDTO> range =
        this.flowNodeInstanceStore.range(start, end)) {
      range.forEachRemaining(r -> this.flowNodeInstanceStore.delete(r.key));
    }
    VariableKeyDTO variableStartKey =
        new VariableKeyDTO(processInstanceKey, MIN_UUID, MIN_UUID, "");
    VariableKeyDTO variableEndKey =
        new VariableKeyDTO(processInstanceKey, MAX_UUID, MAX_UUID, "\u00ff");

    try (KeyValueIterator<VariableKeyDTO, JsonNode> range =
        this.variablesStore.range(variableStartKey, variableEndKey)) {
      range.forEachRemaining(
          r -> {
            this.variablesStore.delete(r.key);
          });
    }
  }
}
