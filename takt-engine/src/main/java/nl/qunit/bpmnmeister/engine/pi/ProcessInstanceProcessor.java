package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.generic.TenantNamespaceNameWrapper;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pd.model.FlowElements;
import nl.qunit.bpmnmeister.engine.pd.model.WIthChildElements;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstances;
import nl.qunit.bpmnmeister.engine.pi.model.ProcessInstance;
import nl.qunit.bpmnmeister.engine.pi.model.WithFlowNodeInstances;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTriggerDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTriggerDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTriggerDTO;
import nl.qunit.bpmnmeister.pi.TerminateTriggerDTO;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstancesDTO;
import nl.qunit.bpmnmeister.pi.state.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;
import nl.qunit.bpmnmeister.pi.state.WithFlowNodeInstancesDTO;
import org.apache.kafka.streams.processor.PunctuationType;
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
  private KeyValueStore<String, FlowNodeInstanceDTO> flowNodeInstanceStore;
  private KeyValueStore<String, JsonNode> variablesStore;
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

    context.schedule(
        Duration.ofSeconds(300),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp ->
            this.processInstanceStore
                .all()
                .forEachRemaining(
                    record -> {
                      if (record.value.getFlowNodeInstances().getState().isFinished()) {
                        this.processInstanceStore.delete(record.key);
                        this.variablesStore
                            .range(record.key + ":", record.key + "\u00ff")
                            .forEachRemaining(r -> this.variablesStore.delete(r.key));
                      }
                    }));
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTriggerDTO> triggerRecord) {
    ProcessInstanceTriggerDTO trigger = triggerRecord.value();

    try {
      switch (trigger) {
        case StartNewProcessInstanceTriggerDTO startNewProcessInstanceTrigger ->
            handleStartNewProcessInstance(startNewProcessInstanceTrigger);
        case ContinueFlowElementTriggerDTO continueFlowElementTrigger2 -> handleContinue(continueFlowElementTrigger2);
        case TerminateTriggerDTO terminateTrigger -> handleTerminate(terminateTrigger);
        default -> throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
      }
    } catch (ProcessInstanceException e) {
      handleExceptional(e, trigger);
    } catch (Throwable t) {
      log.error("Internal error occurred for", t);
    }
  }

  private void handleExceptional(ProcessInstanceException e, ProcessInstanceTriggerDTO trigger) {
    FLowNodeInstance<?> flowNodeInstance = e.getFlowNodeInstance();
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
    Variables processInstanceVariablee =
        variablesMapper.fromDTO(startNewProcessInstanceTrigger.getVariables());

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

  private ProcessInstanceUpdate processInstanceToUpdate(
      ProcessInstance processInstance, FlowNodeInstances flowNodeInstances, Variables variables) {
    VariablesDTO variablesDTO = variablesMapper.toDTO(variables);
    FlowNodeInstancesDTO flowNodeInstancesDTO =
        new FlowNodeInstancesDTO(
            flowNodeInstances.getState(), flowNodeInstances.getFlowNodeInstancesId());
    ProcessInstanceDTO processInstanceDTO =
        instanceMapper.map(processInstance).toBuilder()
            .flowNodeInstances(flowNodeInstancesDTO)
            .build();
    return new ProcessInstanceUpdate(processInstanceDTO, variablesDTO);
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
            flowNodeInstances);

        processResultAndForward(
            instanceResult,
            processInstance.getProcessDefinitionKey(),
            processInstance,
            flowNodeInstances,
            processInstanceVariables);
      }
    }
  }

  private VariablesDTO getVariablesForProcessInstanceKey(UUID processInstanceKey) {
    Map<String, JsonNode> variables = new HashMap<>();
    variablesStore
        .range(processInstanceKey + ":", processInstanceKey + "\u00ff")
        .forEachRemaining(
            r -> {
              String varName = r.key.substring(r.key.indexOf(":") + 1);
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
    log.info("Retrieving flow node instances with id: {}", flowNodeInstancesId);
    flowNodeInstanceStore
        .range(flowNodeInstancesId.toString() + ":", flowNodeInstancesId + "\u00ff")
        .forEachRemaining(
            record -> {
              log.info("Retrieved flow node instance: {}", record.value);
              FlowNodeInstanceDTO instanceDTO = record.value;
              FLowNodeInstance instance = instanceMapper.map(instanceDTO, flowElements);
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
    VariablesDTO variablesDTO = variablesMapper.toDTO(processInstanceVariables);
    if (flowNodeInstances.isDirty()) {
      instanceResult.addProcessInstanceUpdate(
          new ProcessInstanceUpdate(processInstanceDTO, variablesDTO));
    }

    processInstanceStore.put(processInstance.getProcessInstanceKey(), processInstanceDTO);
    storeFlowNodeInstances(processInstance.getFlowNodeInstances());

    variablesDTO
        .getVariables()
        .forEach(
            (k, v) -> {
              variablesStore.put(processInstance.getProcessInstanceKey() + ":" + k, v);
            });

    forwarder.forward(context, instanceResult, processDefinitionKey, processInstanceDTO);
  }

  private void storeFlowNodeInstances(FlowNodeInstances flowNodeInstances) {
    log.info(
        "Storing {} flow node instances with id: {}",
        flowNodeInstances.getInstances().size(),
        flowNodeInstances.getFlowNodeInstancesId());
    for (FLowNodeInstance<?> fLowNodeInstance : flowNodeInstances.getInstances().values()) {
      FlowNodeInstanceDTO flowNodeInstanceDTO = instanceMapper.map(fLowNodeInstance);
      String key =
          flowNodeInstances.getFlowNodeInstancesId()
              + ":"
              + flowNodeInstanceDTO.getElementInstanceId();
      log.info("Storing flow node instance: {} {}", key, flowNodeInstanceDTO);
      flowNodeInstanceStore.put(key, flowNodeInstanceDTO);
      if (fLowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances) {
        storeFlowNodeInstances(withFlowNodeInstances.getFlowNodeInstances());
      }
    }
  }
}
