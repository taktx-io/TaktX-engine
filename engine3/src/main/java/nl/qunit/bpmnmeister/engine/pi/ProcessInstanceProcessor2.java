package nl.qunit.bpmnmeister.engine.pi;

import java.time.Instant;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.FLowNodeInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessInstanceProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElements2;
import nl.qunit.bpmnmeister.pd.model.FlowNode2;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.ProcessInstance2;
import nl.qunit.bpmnmeister.pi.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger2;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger2;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ProcessInstanceProcessor2
    implements Processor<UUID, ProcessInstanceTrigger2, Object, Object> {

  private final DefinitionMapper definitionMapper;
  private final ProcessInstanceMapper instanceMapper;
  private final VariablesMapper variablesMapper;
  private final ProcessInstanceProcessorProvider processInstanceProcessorProvider;
  private final Forwarder forwarder;
  private final FlowInstanceRunner flowInstanceRunner;

  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<UUID, VariablesDTO> variablesStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> processInstanceDefinitionStore;
  private ProcessorContext<Object, Object> context;

  public ProcessInstanceProcessor2(
      DefinitionMapper definitionMapper,
      ProcessInstanceMapper instanceMapper,
      VariablesMapper variablesMapper,
      ProcessInstanceProcessorProvider processInstanceProcessorProvider,
      Forwarder forwarder,
      FlowInstanceRunner flowInstanceRunner) {
    this.definitionMapper = definitionMapper;
    this.instanceMapper = instanceMapper;
    this.variablesMapper = variablesMapper;
    this.processInstanceProcessorProvider = processInstanceProcessorProvider;
    this.forwarder = forwarder;
    this.flowInstanceRunner = flowInstanceRunner;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.variablesStore = context.getStateStore(Stores.VARIABLES_STORE_NAME);
    this.processInstanceStore = context.getStateStore(Stores.PROCESS_INSTANCE_STORE_NAME);
    this.processInstanceDefinitionStore =
        context.getStateStore(Stores.PROCESS_INSTANCE_DEFINITION_STORE_NAME);
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTrigger2> triggerRecord) {
    ProcessInstanceTrigger2 trigger = triggerRecord.value();

    switch (trigger) {
      case StartNewProcessInstanceTrigger2
      startNewProcessInstanceTrigger -> handleStartNewProcessInstance(
          startNewProcessInstanceTrigger);
      case ContinueFlowElementTrigger2 continueFlowElementTrigger2 -> handleContinue(
          continueFlowElementTrigger2);
      case TerminateTrigger terminateTrigger -> handleTerminate(terminateTrigger);
      default -> throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
    }
  }

  public void handleStartNewProcessInstance(
      StartNewProcessInstanceTrigger2 startNewProcessInstanceTrigger) {
    ProcessDefinitionDTO definitionDTO = startNewProcessInstanceTrigger.getProcessDefinition();
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(definitionDTO);
    processInstanceDefinitionStore.putIfAbsent(processDefinitionKey, definitionDTO);

    FlowElements2 flowElements = definitionMapper.getFlowElements(definitionDTO.getDefinitions());

    FlowNodeStates2 flowNodeStates = new FlowNodeStates2();
    Variables2 processInstanceVariablee =
        variablesMapper.fromDTO(startNewProcessInstanceTrigger.getVariables());

    ProcessInstance2 processInstance =
        new ProcessInstance2(
            startNewProcessInstanceTrigger.getProcessInstanceKey(),
            startNewProcessInstanceTrigger.getParentProcessInstanceKey(),
            startNewProcessInstanceTrigger.getParentElementIdPath(),
            startNewProcessInstanceTrigger.getParentElementInstancePath(),
            processDefinitionKey,
            flowNodeStates);

    FlowNode2 flowNode = flowElements.getStartNode(startNewProcessInstanceTrigger.getElementId());
    FLowNodeInstance<?> flowNodeInstance = flowNode.createAndStoreNewInstance(null, flowNodeStates);

    FLowNodeInstanceProcessor<?, ?, ?> processor =
        processInstanceProcessorProvider.getProcessor(flowNode);

    InstanceResult instanceResult =
        processor.processStart(
            flowElements,
            flowNodeInstance,
            Constants.NONE,
            processInstanceVariablee,
            false,
            flowNodeStates);

    continueNewInstances(
        instanceResult,
        flowNodeStates,
        flowElements,
        processInstance,
        processDefinitionKey,
        processInstanceVariablee);
  }

  public void handleContinue(ContinueFlowElementTrigger2 trigger) {
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      ProcessDefinitionDTO processDefinitionDTO =
          processInstanceDefinitionStore.get(processInstanceDTO.getProcessDefinitionKey());
      if (processDefinitionDTO != null) {
        FlowElements2 flowElements =
            definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions());

        VariablesDTO variablesDTO = variablesStore.get(trigger.getProcessInstanceKey());
        ProcessInstance2 processInstance = instanceMapper.map(processInstanceDTO, flowElements);
        FlowNodeStates2 flowNodeStates = processInstance.getFlowNodeStates();
        FLowNodeInstance<?> flowNodeInstance =
            flowNodeStates.getInstanceWithInstanceId(trigger.getElementInstanceIdPath().getFirst());
        Variables2 processInstanceVariables = variablesMapper.fromDTO(variablesDTO);

        FLowNodeInstanceProcessor<?, ?, ?> processor =
            processInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

        InstanceResult instanceResult =
            processor.processContinue(
                0,
                flowElements,
                flowNodeInstance,
                trigger,
                processInstanceVariables,
                false,
                flowNodeStates);

        continueNewInstances(
            instanceResult,
            flowNodeStates,
            flowElements,
            processInstance,
            processInstanceDTO.getProcessDefinitionKey(),
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
        FlowElements2 flowElements =
            definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions());

        ProcessInstance2 processInstance = instanceMapper.map(processInstanceDTO, flowElements);
        InstanceResult instanceResult = InstanceResult.empty();
        if (trigger.getElementIdPath().isEmpty() && trigger.getElementInstanceIdPath().isEmpty()) {
          // Terminate all elements in the process instance and the process instance itself
          processInstance
              .getFlowNodeStates()
              .getFlowNodeInstances()
              .values()
              .forEach(
                  instance -> {
                    FLowNodeInstanceProcessor<?, ?, ?> processor =
                        processInstanceProcessorProvider.getProcessor(instance.getFlowNode());
                    instanceResult.merge(processor.processTerminate(instance));
                  });
          processInstance.getFlowNodeStates().setState(ProcessInstanceState.TERMINATED);
        } else {
          // Terminate the specific element instance in the process instance
          FLowNodeInstance<?> instance =
              processInstance
                  .getFlowNodeStates()
                  .getInstanceWithInstanceId(trigger.getElementInstanceIdPath().getFirst());
          if (instance != null) {
            FLowNodeInstanceProcessor<?, ?, ?> processor =
                processInstanceProcessorProvider.getProcessor(instance.getFlowNode());
            instanceResult.merge(processor.processTerminate(instance));
          }
        }
        continueNewInstances(
            instanceResult,
            processInstance.getFlowNodeStates(),
            flowElements,
            processInstance,
            processInstanceDTO.getProcessDefinitionKey(),
            variablesMapper.fromDTO(variablesStore.get(trigger.getProcessInstanceKey())));
      }
    }
  }

  protected void continueNewInstances(
      InstanceResult instanceResult,
      FlowNodeStates2 flowNodeStates,
      FlowElements2 flowElements,
      ProcessInstance2 processInstance,
      ProcessDefinitionKey processDefinitionKey,
      Variables2 processInstanceVariables) {
    while (instanceResult.hasNewFlowNodeInstances()) {
      forwarder.forward(context, instanceResult, processDefinitionKey, processInstance);

      instanceResult =
          flowInstanceRunner.processInstanceResult(
              flowNodeStates,
              instanceResult,
              flowElements,
              processInstanceVariables,
              flowNodeStates);
    }

    flowNodeStates.determineImplicitCompletedState();

    if (flowNodeStates.getState() == ProcessInstanceState.COMPLETED) {
      processInstanceFinished(instanceResult, processInstance, processInstanceVariables);
    }

    ProcessInstanceDTO piDto = instanceMapper.map(processInstance);
    processInstanceStore.put(processInstance.getProcessInstanceKey(), piDto);
    VariablesDTO variablesDTO = variablesMapper.toDTO(processInstanceVariables);
    variablesStore.put(processInstance.getProcessInstanceKey(), variablesDTO);

    forwarder.forward(context, instanceResult, processDefinitionKey, processInstance);

    context.forward(
        new Record<>(
            processInstance.getProcessInstanceKey(),
            new ProcessInstanceUpdate(piDto, variablesDTO),
            Instant.now().toEpochMilli()));
  }

  private void processInstanceFinished(
      InstanceResult instanceResult,
      ProcessInstance2 processInstance,
      Variables2 processInstanceVariables) {
    if (!processInstance.getParentProcessInstanceKey().equals(Constants.NONE_UUID)) {
      instanceResult.addContinuation(
          new ContinueFlowElementTrigger2(
              processInstance.getParentProcessInstanceKey(),
              processInstance.getParentElementIdPath(),
              processInstance.getParentElementInstancePath(),
              Constants.NONE,
              variablesMapper.toDTO(processInstanceVariables)));
    }
  }
}
