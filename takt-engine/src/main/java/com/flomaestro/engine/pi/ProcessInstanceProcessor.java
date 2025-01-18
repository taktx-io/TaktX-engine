package com.flomaestro.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.engine.pd.Stores;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.WIthChildElements;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.engine.pi.model.WithFlowNodeInstances;
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
import com.flomaestro.takt.dto.v_1_0_0.WithFlowNodeInstancesDTO;
import com.flomaestro.takt.util.TaktUUIDSerde;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final VariablesMapper variablesMapper;
  private final Forwarder forwarder;
  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final FlowNodeInstancesProcessor flowNodeInstancesProcessor;
  private final Clock clock;
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
      DefinitionMapper definitionMapper,
      ProcessInstanceMapper instanceMapper,
      VariablesMapper variablesMapper,
      Forwarder forwarder,
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper,
      FlowNodeInstancesProcessor flowNodeInstancesProcessor,
      Clock clock) {
    this.definitionMapper = definitionMapper;
    this.instanceMapper = instanceMapper;
    this.variablesMapper = variablesMapper;
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
            handleTerminate(terminateTrigger, processingStatistics);
        default ->
            throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
      }
    } catch (ProcessInstanceException e) {
      handleExceptional(e, processingStatistics);
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

    Variables processInstanceVariables =
        Variables.empty(key -> variablesStore.get(new VariableKeyDTO(processInstanceKey, key)));
    processInstanceVariables.merge(startCommand.getVariables());

    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(processDefinition);

    ProcessInstance processInstance =
        new ProcessInstance(
            processInstanceKey,
            startCommand.getParentProcessInstanceKey(),
            startCommand.getParentElementIdPath(),
            startCommand.getParentElementInstancePath(),
            processDefinitionKey,
            flowNodeInstances);

    InstanceResult instanceResult = InstanceResult.empty();

    instanceResult.addInstanceUpdate(
        processInstanceToUpdate(
            processInstance, flowNodeInstances, processInstanceVariables, clock.millis()));

    FlowElements flowElements =
        definitionMapper.getFlowElements(processDefinition.getDefinitions());

    flowNodeInstancesProcessor.processStart(
        instanceResult,
        startEventId,
        null,
        flowElements,
        processInstance,
        processInstanceVariables,
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
      ProcessInstanceException e, ProcessingStatistics processingStatistics) {
    FlowNodeInstance<?> flowNodeInstance = e.getFlowNodeInstance();
    flowNodeInstance.terminate();
    ProcessInstance processInstance = e.getProcessInstance();
    log.warn(
        "Takt exception occurred for processinstance {}, {}, {}",
        processInstance.getProcessInstanceKey(),
        flowNodeInstance.getFlowNode().getId(),
        flowNodeInstance.getElementInstanceId());
    handleTerminate(
        new TerminateTriggerDTO(processInstance.getProcessInstanceKey(), List.of()),
        processingStatistics);
  }

  private ProcessInstanceUpdateDTO processInstanceToUpdate(
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      Variables variables,
      long processTime) {

    VariablesDTO variablesDTO = variablesMapper.toDTO(variables);

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
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {
        FlowNodeInstances flowNodeInstances =
            retrieveFlowNodeInstances(processInstanceDTO.getFlowNodeInstances(), flowElements);
        Variables processInstanceVariables =
            Variables.empty(
                key ->
                    variablesStore.get(new VariableKeyDTO(trigger.getProcessInstanceKey(), key)));

        ProcessInstance processInstance =
            instanceMapper.mapAndSetReferences(processInstanceDTO, flowNodeInstances, flowElements);

        flowNodeInstancesProcessor.processContinue(
            instanceResult,
            0,
            trigger,
            flowElements,
            processInstance,
            processInstanceVariables,
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
      log.warn("Process instance not found for key: {}", trigger.getProcessInstanceKey());
    }
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
      TerminateTriggerDTO trigger, ProcessingStatistics processingStatistics) {
    InstanceResult instanceResult = InstanceResult.empty();
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      FlowElements flowElements = getFlowElements(processInstanceDTO.getProcessDefinitionKey());
      if (flowElements != null) {
        Variables processInstanceVariables =
            Variables.empty(
                key ->
                    variablesStore.get(new VariableKeyDTO(trigger.getProcessInstanceKey(), key)));
        FlowNodeInstances flowNodeInstances =
            retrieveFlowNodeInstances(processInstanceDTO.getFlowNodeInstances(), flowElements);
        ProcessInstance processInstance =
            instanceMapper.mapAndSetReferences(processInstanceDTO, flowNodeInstances, flowElements);

        flowNodeInstancesProcessor.processTerminate(
            instanceResult,
            trigger,
            processInstance,
            processInstance.getFlowNodeInstances(),
            processInstanceVariables,
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

  private FlowNodeInstances retrieveFlowNodeInstances(
      FlowNodeInstancesDTO flowNodeInstancesDTO, FlowElements flowElements) {
    FlowNodeInstances flowNodeInstances = new FlowNodeInstances();
    UUID flowNodeInstancesId = flowNodeInstancesDTO.getFlowNodeInstancesId();
    flowNodeInstances.setFlowNodeInstancesId(flowNodeInstancesId);
    flowNodeInstances.setState(flowNodeInstancesDTO.getState());
    UUID[] start = new UUID[] {flowNodeInstancesId, TaktUUIDSerde.MIN_UUID};
    UUID[] end = new UUID[] {flowNodeInstancesId, TaktUUIDSerde.MAX_UUID};
    try (KeyValueIterator<UUID[], FlowNodeInstanceDTO> range =
        flowNodeInstanceStore.range(start, end)) {
      range.forEachRemaining(
          flowNodeRecord -> {
            FlowNodeInstanceDTO instanceDTO = flowNodeRecord.value;
            FlowNodeInstance<?> instance = instanceMapper.map(instanceDTO, flowElements);
            flowNodeInstances.putInstance(instance);
            if (instanceDTO instanceof WithFlowNodeInstancesDTO withFlowNodeInstancesDTO) {
              WithFlowNodeInstances withFlowNodeInstances = (WithFlowNodeInstances) instance;
              FlowElements subFlowElements = flowElements;
              if (instance.getFlowNode() instanceof WIthChildElements withChildElements) {
                subFlowElements = withChildElements.getElements();
              }
              FlowNodeInstances subFlowNodeInstances =
                  retrieveFlowNodeInstances(
                      withFlowNodeInstancesDTO.getFlowNodeInstances(), subFlowElements);
              withFlowNodeInstances
                  .getFlowNodeInstances()
                  .getInstances()
                  .putAll(subFlowNodeInstances.getInstances());
            }
          });
    }

    return flowNodeInstances;
  }

  private void processResultAndForward(
      InstanceResult instanceResult,
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      Variables processInstanceVariables,
      ProcessingStatistics processingStatistics) {

    FlowNodeInstancesDTO flowNodeInstancesDTO = flowNodeInstancesToDTO(flowNodeInstances);

    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder()
            .flowNodeInstances(flowNodeInstancesDTO)
            .build();

    Map<String, JsonNode> dirtyVariables =
        processInstanceVariables.entrySet().stream()
            .filter(e -> processInstanceVariables.isDirty(e.getKey()))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    VariablesDTO dirtyVariablesDTO = VariablesDTO.of(dirtyVariables);

    if (flowNodeInstances.isStateChanged()) {
      if (flowNodeInstances.getState() == ProcessInstanceState.COMPLETED) {
        processingStatistics.increaseProcessInstancesFinished();
      }
      instanceResult.addInstanceUpdate(
          new ProcessInstanceUpdateDTO(processInstanceDTO, dirtyVariablesDTO, clock.millis()));
    }

    forwarder.forward(context, instanceResult, processDefinitionKey, processInstanceDTO);

    if (processInstance.getFlowNodeInstances().getState().isFinished()) {
      purgeProcessInstance(processInstanceDTO);
    } else {
      processInstanceStore.put(processInstance.getProcessInstanceKey(), processInstanceDTO);
      storeFlowNodeInstances(processInstance.getFlowNodeInstances());
      dirtyVariablesDTO
          .getVariables()
          .forEach(
              (k, v) ->
                  variablesStore.put(
                      new VariableKeyDTO(processInstance.getProcessInstanceKey(), k), v));
    }
  }

  private static FlowNodeInstancesDTO flowNodeInstancesToDTO(FlowNodeInstances flowNodeInstances) {
    return new FlowNodeInstancesDTO(
        flowNodeInstances.getState(), flowNodeInstances.getFlowNodeInstancesId(), flowNodeInstances.getActiveCnt());
  }

  private void storeFlowNodeInstances(FlowNodeInstances flowNodeInstances) {
    for (FlowNodeInstance<?> fLowNodeInstance : flowNodeInstances.getInstances().values()) {
      if (!fLowNodeInstance.isDirty()) {
        continue;
      }
      FlowNodeInstanceDTO flowNodeInstanceDTO = instanceMapper.map(fLowNodeInstance);

      UUID[] key =
          new UUID[] {
            flowNodeInstances.getFlowNodeInstancesId(), flowNodeInstanceDTO.getElementInstanceId()
          };
      flowNodeInstanceStore.put(key, flowNodeInstanceDTO);
      if (fLowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances) {
        storeFlowNodeInstances(withFlowNodeInstances.getFlowNodeInstances());
      }
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
    VariableKeyDTO variableStartKey = new VariableKeyDTO(processInstanceKey, "");
    VariableKeyDTO variableEndKey = new VariableKeyDTO(processInstanceKey, "\u00ff");

    try (KeyValueIterator<VariableKeyDTO, JsonNode> range =
        this.variablesStore.range(variableStartKey, variableEndKey)) {
      range.forEachRemaining(
          r -> {
            this.variablesStore.delete(r.key);
          });
    }
  }
}
