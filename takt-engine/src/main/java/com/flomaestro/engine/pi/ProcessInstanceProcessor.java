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
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstancesDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartNewProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import com.flomaestro.takt.dto.v_1_0_0.WithFlowNodeInstancesDTO;
import com.flomaestro.takt.util.TaktUUIDSerde;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ProcessInstanceProcessor
    implements Processor<UUID, ProcessInstanceTriggerDTO, Object, Object> {

  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final VariablesMapper variablesMapper;
  private final Forwarder forwarder;
  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final FlowNodeInstancesProcessor flowNodeInstancesProcessor;
  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<UUID[], FlowNodeInstanceDTO> flowNodeInstanceStore;
  private KeyValueStore<VariableKeyDTO, JsonNode> variablesStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> processInstanceDefinitionStore;
  private ProcessorContext<Object, Object> context;

  public ProcessInstanceProcessor(
      DefinitionMapper definitionMapper,
      ProcessInstanceMapper instanceMapper,
      VariablesMapper variablesMapper,
      Forwarder forwarder,
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper,
      FlowNodeInstancesProcessor flowNodeInstancesProcessor) {
    this.definitionMapper = definitionMapper;
    this.instanceMapper = instanceMapper;
    this.variablesMapper = variablesMapper;
    this.forwarder = forwarder;
    this.tenantNamespaceNameWrapper = tenantNamespaceNameWrapper;
    this.flowNodeInstancesProcessor = flowNodeInstancesProcessor;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.variablesStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.VARIABLES.getStorename()));
    this.processInstanceStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_INSTANCE.getStorename()));
    this.flowNodeInstanceStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.FLOW_NODE_INSTANCE.getStorename()));
    this.processInstanceDefinitionStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(
                Stores.PROCESS_INSTANCE_DEFINITION.getStorename()));
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

    this.flowNodeInstanceStore
        .range(start, end)
        .forEachRemaining(
            r -> {
              log.debug("Purging flow node instance: {}", r.key);
              this.flowNodeInstanceStore.delete(r.key);
            });
    VariableKeyDTO variableStartKey = new VariableKeyDTO(processInstanceKey, "");
    VariableKeyDTO variableEndKey = new VariableKeyDTO(processInstanceKey, "\u00ff");
    this.variablesStore
        .range(variableStartKey, variableEndKey)
        .forEachRemaining(
            r -> {
              log.debug("Purging variable: {}", r.key);
              this.variablesStore.delete(r.key);
            });
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTriggerDTO> triggerRecord) {
    ProcessInstanceTriggerDTO trigger = triggerRecord.value();

    try {
      switch (trigger) {
        case StartNewProcessInstanceTriggerDTO startNewProcessInstanceTrigger ->
            handleStartNewProcessInstance(startNewProcessInstanceTrigger);
        case ContinueFlowElementTriggerDTO continueFlowElementTrigger2 ->
            handleContinue(continueFlowElementTrigger2);
        case TerminateTriggerDTO terminateTrigger -> handleTerminate(terminateTrigger);
        default ->
            throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
      }
    } catch (ProcessInstanceException e) {
      handleExceptional(e, trigger);
    } catch (Throwable t) {
      log.error("Internal error occurred for", t);
    }
  }

  private void handleExceptional(ProcessInstanceException e, ProcessInstanceTriggerDTO trigger) {
    FlowNodeInstance<?> flowNodeInstance = e.getFlowNodeInstance();
    flowNodeInstance.terminate();
    ProcessInstance processInstance = e.getProcessInstance();
    log.warn(
        "Takt exception occurred for processinstance {}, {}, {}",
        processInstance.getProcessInstanceKey(),
        flowNodeInstance.getFlowNode().getId(),
        flowNodeInstance.getElementInstanceId());
    handleTerminate(new TerminateTriggerDTO(processInstance.getProcessInstanceKey(), List.of()));
  }

  public void handleStartNewProcessInstance(
      StartNewProcessInstanceTriggerDTO startNewProcessInstanceTrigger) {
    ProcessDefinitionDTO definitionDTO = startNewProcessInstanceTrigger.getProcessDefinition();
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(definitionDTO);
    processInstanceDefinitionStore.putIfAbsent(processDefinitionKey, definitionDTO);

    FlowElements flowElements = definitionMapper.getFlowElements(definitionDTO.getDefinitions());

    FlowNodeInstances flowNodeInstances = new FlowNodeInstances();
    Variables triggerVariables =
        variablesMapper.fromDTO(startNewProcessInstanceTrigger.getVariables());
    Variables processInstanceVariablee = Variables.empty();
    processInstanceVariablee.merge(triggerVariables);

    ProcessInstance processInstance =
        new ProcessInstance(
            startNewProcessInstanceTrigger.getProcessInstanceKey(),
            startNewProcessInstanceTrigger.getParentProcessInstanceKey(),
            startNewProcessInstanceTrigger.getParentElementIdPath(),
            startNewProcessInstanceTrigger.getParentElementInstancePath(),
            processDefinitionKey,
            flowNodeInstances);

    InstanceResult instanceResult = InstanceResult.empty();

    instanceResult.addProcessInstanceUpdate(
        processInstanceToUpdate(processInstance, flowNodeInstances, processInstanceVariablee));

    flowNodeInstancesProcessor.processStart(
        instanceResult,
        startNewProcessInstanceTrigger.getElementId(),
        null,
        flowElements,
        processInstance,
        processInstanceVariablee,
        flowNodeInstances);

    processResultAndForward(
        instanceResult,
        processDefinitionKey,
        processInstance,
        flowNodeInstances,
        processInstanceVariablee);
  }

  private ProcessInstanceUpdateDTO processInstanceToUpdate(
      ProcessInstance processInstance, FlowNodeInstances flowNodeInstances, Variables variables) {

    VariablesDTO variablesDTO = variablesMapper.toDTO(variables);

    FlowNodeInstancesDTO flowNodeInstancesDTO =
        new FlowNodeInstancesDTO(
            flowNodeInstances.getState(), flowNodeInstances.getFlowNodeInstancesId());
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder()
            .flowNodeInstances(flowNodeInstancesDTO)
            .build();
    return new ProcessInstanceUpdateDTO(processInstanceDTO, variablesDTO);
  }

  public void handleContinue(ContinueFlowElementTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      ProcessDefinitionDTO processDefinitionDTO =
          processInstanceDefinitionStore.get(processInstanceDTO.getProcessDefinitionKey());
      if (processDefinitionDTO != null) {
        FlowElements flowElements =
            definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions());

        VariablesDTO variablesDTO =
            getVariablesForProcessInstanceKey(trigger.getProcessInstanceKey());

        FlowNodeInstances flowNodeInstances =
            retrieveFlowNodeInstances(processInstanceDTO.getFlowNodeInstances(), flowElements);
        Variables processInstanceVariables = variablesMapper.fromDTO(variablesDTO);

        ProcessInstance processInstance =
            instanceMapper.mapAndSetReferences(processInstanceDTO, flowNodeInstances, flowElements);

        flowNodeInstancesProcessor.processContinue(
            instanceResult,
            0,
            trigger,
            flowElements,
            processInstance,
            processInstanceVariables,
            processInstance.getFlowNodeInstances());

        processResultAndForward(
            instanceResult,
            processInstance.getProcessDefinitionKey(),
            processInstance,
            processInstance.getFlowNodeInstances(),
            processInstanceVariables);
      }
    }
  }

  private VariablesDTO getVariablesForProcessInstanceKey(UUID processInstanceKey) {
    Map<String, JsonNode> variables = new HashMap<>();
    VariableKeyDTO variableStartKey = new VariableKeyDTO(processInstanceKey, "");
    VariableKeyDTO variableEndKey = new VariableKeyDTO(processInstanceKey, "\u00ff");
    variablesStore
        .range(variableStartKey, variableEndKey)
        .forEachRemaining(
            r -> {
              String varName = r.key.getVariableName();
              variables.put(varName, r.value);
            });
    VariablesDTO variablesDTO = VariablesDTO.of(variables);
    return variablesDTO;
  }

  private void handleTerminate(TerminateTriggerDTO trigger) {
    InstanceResult instanceResult = InstanceResult.empty();
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      ProcessDefinitionDTO processDefinitionDTO =
          processInstanceDefinitionStore.get(processInstanceDTO.getProcessDefinitionKey());
      if (processDefinitionDTO != null) {
        FlowElements flowElements =
            definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions());
        VariablesDTO variablesDTO =
            getVariablesForProcessInstanceKey(trigger.getProcessInstanceKey());
        Variables processInstanceVariables = variablesMapper.fromDTO(variablesDTO);
        FlowNodeInstances flowNodeInstances =
            retrieveFlowNodeInstances(processInstanceDTO.getFlowNodeInstances(), flowElements);
        ProcessInstance processInstance =
            instanceMapper.mapAndSetReferences(processInstanceDTO, flowNodeInstances, flowElements);

        flowNodeInstancesProcessor.processTerminate(
            instanceResult, trigger, processInstance, processInstanceVariables, flowElements);

        processResultAndForward(
            instanceResult,
            processInstance.getProcessDefinitionKey(),
            processInstance,
            processInstance.getFlowNodeInstances(),
            processInstanceVariables);
      }
    }
  }

  private FlowNodeInstances retrieveFlowNodeInstances(
      FlowNodeInstancesDTO flowNodeInstancesDTO, FlowElements flowElements) {
    FlowNodeInstances flowNodeInstances = new FlowNodeInstances();
    UUID flowNodeInstancesId = flowNodeInstancesDTO.getFlowNodeInstancesId();
    flowNodeInstances.setFlowNodeInstancesId(flowNodeInstancesId);
    flowNodeInstances.setState(flowNodeInstancesDTO.getState());
    log.debug("Retrieving flow node instances with id: {}", flowNodeInstancesId);
    UUID[] start = new UUID[] {flowNodeInstancesId, TaktUUIDSerde.MIN_UUID};
    UUID[] end = new UUID[] {flowNodeInstancesId, TaktUUIDSerde.MAX_UUID};
    flowNodeInstanceStore
        .range(start, end)
        .forEachRemaining(
            record -> {
              log.debug("Retrieved flow node instance: {}", record.value);
              FlowNodeInstanceDTO instanceDTO = record.value;
              FlowNodeInstance instance = instanceMapper.map(instanceDTO, flowElements);
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

    return flowNodeInstances;
  }

  private void processResultAndForward(
      InstanceResult instanceResult,
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstance processInstance,
      FlowNodeInstances flowNodeInstances,
      Variables processInstanceVariables) {

    FlowNodeInstancesDTO flowNodeInstancesDTO =
        new FlowNodeInstancesDTO(
            flowNodeInstances.getState(), flowNodeInstances.getFlowNodeInstancesId());

    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder()
            .flowNodeInstances(flowNodeInstancesDTO)
            .build();

    Map<String, JsonNode> dirtyVariables =
        processInstanceVariables.entrySet().stream()
            .filter(e -> processInstanceVariables.isDirty(e.getKey()))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    VariablesDTO dirtyVariablesDTO = VariablesDTO.of(dirtyVariables);

    if (flowNodeInstances.isDirty()) {
      instanceResult.addProcessInstanceUpdate(
          new ProcessInstanceUpdateDTO(processInstanceDTO, dirtyVariablesDTO));
    }

    if (processInstance.getFlowNodeInstances().getState().isFinished()) {
      purgeProcessInstance(processInstanceDTO);
    } else {
      processInstanceStore.put(processInstance.getProcessInstanceKey(), processInstanceDTO);
      storeFlowNodeInstances(processInstance.getFlowNodeInstances());
      dirtyVariablesDTO
          .getVariables()
          .forEach(
              (k, v) -> {
                log.debug(
                    "Storing variable: {} {}",
                    processInstance.getProcessInstanceKey() + ":" + k,
                    v);

                variablesStore.put(
                    new VariableKeyDTO(processInstance.getProcessInstanceKey(), k), v);
              });
    }
    forwarder.forward(context, instanceResult, processDefinitionKey, processInstanceDTO);
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
      log.debug("Storing flow node instance: {} {}", key, flowNodeInstanceDTO);
      flowNodeInstanceStore.put(key, flowNodeInstanceDTO);
      if (fLowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances) {
        storeFlowNodeInstances(withFlowNodeInstances.getFlowNodeInstances());
      }
    }
  }
}
