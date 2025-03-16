package com.flomaestro.engine.pi;

import static com.flomaestro.takt.dto.v_1_0_0.Constants.MAX_LONG;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.engine.pd.Stores;
import com.flomaestro.engine.pd.model.FlowElements;
import com.flomaestro.engine.pd.model.IoVariableMapping;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.ProcessInstance;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.engine.pi.model.WithFlowNodeInstances;
import com.flomaestro.engine.pi.processor.IoMappingProcessor;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowElementDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstancesDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
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
  private final ProcessingStatistics processingStatistics;

  private ReadOnlyKeyValueStore<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>>
      definitionsStore;
  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private KeyValueStore<VariableKeyDTO, JsonNode> variablesStore;
  private ProcessorContext<Object, Object> context;
  private final Map<ProcessDefinitionKey, FlowElements> flowElementsCache = new HashMap<>();
  private final Map<ProcessDefinitionKey, ProcessDefinitionDTO> definitionsCache = new HashMap<>();

  public ProcessInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      DtoMapper dtoMapper,
      DefinitionMapper definitionMapper,
      ProcessInstanceMapper instanceMapper,
      Forwarder forwarder,
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper,
      FlowNodeInstancesProcessor flowNodeInstancesProcessor,
      Clock clock,
      ProcessingStatistics processingStatistics) {
    this.ioMappingProcessor = ioMappingProcessor;
    this.definitionMapper = definitionMapper;
    this.dtoMapper = dtoMapper;
    this.instanceMapper = instanceMapper;
    this.forwarder = forwarder;
    this.tenantNamespaceNameWrapper = tenantNamespaceNameWrapper;
    this.flowNodeInstancesProcessor = flowNodeInstancesProcessor;
    this.clock = clock;
    this.processingStatistics = processingStatistics;
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
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTriggerDTO> triggerRecord) {
    ProcessInstanceTriggerDTO trigger = triggerRecord.value();

    try {
      switch (trigger) {
        case StartCommandDTO startCommand ->
            processStartCommandRecord(triggerRecord.key(), startCommand);
        case ContinueFlowElementTriggerDTO continueFlowElementTrigger2 ->
            handleContinue(continueFlowElementTrigger2);
        case TerminateTriggerDTO terminateTrigger ->
            handleTerminate(flowNodeInstanceStore, terminateTrigger);
        default ->
            throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
      }
    } catch (ProcessInstanceException e) {
      handleExceptional(flowNodeInstanceStore, e);
    } catch (Throwable t) {
      log.error("Internal error occurred for", t);
    }
  }

  private void processStartCommandRecord(UUID processInstanceKey, StartCommandDTO startCommand) {
    log.info(
        "Start new process instance: {} definition: {}",
        processInstanceKey,
        startCommand.getProcessDefinitionKey());

    processingStatistics.startTimerForProcessInstance(processInstanceKey);
    processingStatistics.increaseProcessInstancesStarted();

    ProcessDefinitionDTO processDefinition =
        getProcessDefinitionDTO(startCommand.getProcessDefinitionKey());

    String startNodeId =
        startCommand.getElementIdPath() != null ? startCommand.getElementIdPath().getFirst() : null;

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

    flowNodeInstancesProcessor.processStart(
        flowNodeInstanceStore,
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
        processingStatistics,
        flowElements,
        updateVariablesAtStart);
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
        "Takt exception occurred for processinstance {}, {}, {}",
        processInstance.getProcessInstanceKey(),
        flowNodeInstance.getFlowNode().getId(),
        flowNodeInstance.getElementInstanceId());
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

        flowNodeInstancesProcessor.processContinue(
            flowNodeInstanceStore,
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
            processingStatistics,
            flowElements,
            VariablesDTO.empty());
      }
    } else {
      log.warn("Process instance not found for key: {}", processInstanceKey);
    }
  }

  private void mergeVariablesInScope(
      VariableScope processInstanceVariables,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables) {
    VariableScope targetScope = processInstanceVariables;
    if (elementInstanceIdPath != null) {
      for (Long aLong : elementInstanceIdPath) {
        targetScope = targetScope.selectFlowNodeInstancesScope(aLong);
      }
    }
    targetScope.merge(variables);
  }

  private FlowElements getFlowElements(ProcessDefinitionKey processDefinitionKey) {
    return flowElementsCache.computeIfAbsent(
        processDefinitionKey,
        key -> {
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

        flowNodeInstancesProcessor.processTerminate(
            flowNodeInstanceStore,
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
            processingStatistics,
            flowElements,
            VariablesDTO.empty());
      }
    }
  }

  private void processResultAndForward(
      InstanceResult instanceResult,
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope processInstanceVariables,
      ProcessingStatistics processingStatistics,
      FlowElements flowElements,
      VariablesDTO updatedVariablesAtStart) {

    FlowNodeInstancesDTO flowNodeInstancesDTO = flowNodeInstancesToDTO(flowNodeInstances);

    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder()
            .flowNodeInstances(flowNodeInstancesDTO)
            .build();

    processInstanceVariables.persist();

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

  private static FlowNodeInstancesDTO flowNodeInstancesToDTO(FlowNodeInstances flowNodeInstances) {
    return new FlowNodeInstancesDTO(
        flowNodeInstances.getState(),
        flowNodeInstances.getActiveCnt(),
        flowNodeInstances.getElementInstanceCnt());
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
    log.info("Purging finished process instance: {}", processInstanceKey);
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
