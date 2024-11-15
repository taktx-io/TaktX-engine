package nl.qunit.bpmnmeister.engine.pi;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.generic.TenantNamespaceNameWrapper;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ProcessInstanceProcessor
    implements Processor<UUID, ProcessInstanceTrigger, Object, Object> {

  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final VariablesMapper variablesMapper;
  private final Forwarder forwarder;
  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final FlowNodeInstancesProcessor flowNodeInstancesProcessor;
  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<UUID, VariablesDTO> variablesStore;
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
    this.processInstanceDefinitionStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(
                Stores.PROCESS_INSTANCE_DEFINITION.getStorename()));

    context.schedule(
        Duration.ofMinutes(5),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp ->
            this.processInstanceStore
                .all()
                .forEachRemaining(
                    record -> {
                      if (record.value.getFlowNodeInstances().getState().isFinished()) {
                        this.processInstanceStore.delete(record.key);
                        this.variablesStore.delete(record.key);
                      }
                    }));
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTrigger> triggerRecord) {
    ProcessInstanceTrigger trigger = triggerRecord.value();

    switch (trigger) {
      case StartNewProcessInstanceTrigger
      startNewProcessInstanceTrigger -> handleStartNewProcessInstance(
          startNewProcessInstanceTrigger);
      case ContinueFlowElementTrigger continueFlowElementTrigger2 -> handleContinue(
          continueFlowElementTrigger2);
      case TerminateTrigger terminateTrigger -> handleTerminate(terminateTrigger);
      default -> throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
    }
  }

  public void handleStartNewProcessInstance(
      StartNewProcessInstanceTrigger startNewProcessInstanceTrigger) {
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

    InstanceResult instanceResult =
        flowNodeInstancesProcessor.processStart(
            startNewProcessInstanceTrigger,
            flowElements,
            processInstance,
            processInstanceVariablee,
            flowNodeInstances);

    processResultAndForward(
        instanceResult, processDefinitionKey, processInstance, processInstanceVariablee);
  }

  public void handleContinue(ContinueFlowElementTrigger trigger) {
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      ProcessDefinitionDTO processDefinitionDTO =
          processInstanceDefinitionStore.get(processInstanceDTO.getProcessDefinitionKey());
      if (processDefinitionDTO != null) {
        FlowElements flowElements =
            definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions());

        VariablesDTO variablesDTO = variablesStore.get(trigger.getProcessInstanceKey());
        ProcessInstance processInstance =
            instanceMapper.mapAndSetReferences(processInstanceDTO, flowElements);
        FlowNodeInstances flowNodeInstances = processInstance.getFlowNodeInstances();
        Variables processInstanceVariables = variablesMapper.fromDTO(variablesDTO);

        InstanceResult instanceResult =
            flowNodeInstancesProcessor.processContinue(
                trigger,
                flowElements,
                processInstance,
                processInstanceVariables,
                flowNodeInstances);

        processResultAndForward(
            instanceResult,
            processInstance.getProcessDefinitionKey(),
            processInstance,
            processInstanceVariables);
      }
    }
  }

  private void handleTerminate(TerminateTrigger trigger) {
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      ProcessDefinitionDTO processDefinitionDTO =
          processInstanceDefinitionStore.get(processInstanceDTO.getProcessDefinitionKey());
      if (processDefinitionDTO != null) {
        FlowElements flowElements =
            definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions());
        VariablesDTO variablesDTO = variablesStore.get(trigger.getProcessInstanceKey());
        Variables processInstanceVariables = variablesMapper.fromDTO(variablesDTO);

        ProcessInstance processInstance =
            instanceMapper.mapAndSetReferences(processInstanceDTO, flowElements);

        InstanceResult instanceResult =
            flowNodeInstancesProcessor.processTerminate(
                trigger, processInstance, processInstanceVariables, flowElements);

        processResultAndForward(
            instanceResult,
            processInstance.getProcessDefinitionKey(),
            processInstance,
            processInstanceVariables);
      }
    }
  }

  private void processResultAndForward(
      InstanceResult instanceResult,
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstance processInstance,
      Variables processInstanceVariables) {

    forwarder.forward(context, instanceResult, processDefinitionKey, processInstance);

    ProcessInstanceDTO piDto = instanceMapper.map(processInstance);
    processInstanceStore.put(processInstance.getProcessInstanceKey(), piDto);
    VariablesDTO variablesDTO = variablesMapper.toDTO(processInstanceVariables);
    variablesStore.put(processInstance.getProcessInstanceKey(), variablesDTO);

    context.forward(
        new Record<>(
            processInstance.getProcessInstanceKey(),
            new ProcessInstanceUpdate(piDto, variablesDTO),
            Instant.now().toEpochMilli()));
  }
}
