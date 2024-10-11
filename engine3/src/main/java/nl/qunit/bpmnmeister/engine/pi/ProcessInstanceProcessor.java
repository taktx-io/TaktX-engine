package nl.qunit.bpmnmeister.engine.pi;

import java.time.Instant;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.FLowNodeInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessInstanceProcessorProvider;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.InstanceResult;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ProcessInstanceProcessor
    implements Processor<UUID, ProcessInstanceTrigger, Object, Object> {

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

  public ProcessInstanceProcessor(
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

    FlowNode flowNode = flowElements.getStartNode(startNewProcessInstanceTrigger.getElementId());
    FLowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(null, flowNodeInstances);

    FLowNodeInstanceProcessor<?, ?, ?> processor =
        processInstanceProcessorProvider.getProcessor(flowNode);

    InstanceResult instanceResult =
        processor.processStart(
            flowElements,
            flowNodeInstance,
            Constants.NONE,
            processInstanceVariablee,
            false,
            flowNodeInstances);

    continueNewInstances(
        instanceResult,
        flowNodeInstances,
        flowElements,
        processInstance,
        processDefinitionKey,
        processInstanceVariablee);
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
        FLowNodeInstance<?> flowNodeInstance =
            flowNodeInstances.getInstanceWithInstanceId(
                trigger.getElementInstanceIdPath().getFirst());
        Variables processInstanceVariables = variablesMapper.fromDTO(variablesDTO);

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
                flowNodeInstances);

        continueNewInstances(
            instanceResult,
            flowNodeInstances,
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
        FlowElements flowElements =
            definitionMapper.getFlowElements(processDefinitionDTO.getDefinitions());
        VariablesDTO variablesDTO = variablesStore.get(trigger.getProcessInstanceKey());
        Variables processInstanceVariables = variablesMapper.fromDTO(variablesDTO);

        ProcessInstance processInstance =
            instanceMapper.mapAndSetReferences(processInstanceDTO, flowElements);
        InstanceResult instanceResult = InstanceResult.empty();
        if (trigger.getElementIdPath().isEmpty() && trigger.getElementInstanceIdPath().isEmpty()) {
          // Terminate all elements in the process instance and the process instance itself
          processInstance
              .getFlowNodeInstances()
              .getInstances()
              .values()
              .forEach(
                  instance -> {
                    FLowNodeInstanceProcessor<?, ?, ?> processor =
                        processInstanceProcessorProvider.getProcessor(instance.getFlowNode());
                    instanceResult.merge(
                        processor.processTerminate(instance, processInstanceVariables));
                  });
          processInstance.getFlowNodeInstances().setState(ProcessInstanceState.TERMINATED);
        } else {
          // Terminate the specific element instance in the process instance
          FLowNodeInstance<?> instance =
              processInstance
                  .getFlowNodeInstances()
                  .getInstanceWithInstanceId(trigger.getElementInstanceIdPath().getFirst());
          if (instance != null) {
            FLowNodeInstanceProcessor<?, ?, ?> processor =
                processInstanceProcessorProvider.getProcessor(instance.getFlowNode());
            instanceResult.merge(processor.processTerminate(instance, processInstanceVariables));
          }
        }
        continueNewInstances(
            instanceResult,
            processInstance.getFlowNodeInstances(),
            flowElements,
            processInstance,
            processInstanceDTO.getProcessDefinitionKey(),
            variablesMapper.fromDTO(variablesStore.get(trigger.getProcessInstanceKey())));
      }
    }
  }

  protected void continueNewInstances(
      InstanceResult instanceResult,
      FlowNodeInstances flowNodeInstances,
      FlowElements flowElements,
      ProcessInstance processInstance,
      ProcessDefinitionKey processDefinitionKey,
      Variables processInstanceVariables) {
    instanceResult =
        flowInstanceRunner.continueNewInstances(
            instanceResult, flowNodeInstances, flowElements, processInstanceVariables);

    if (flowNodeInstances.getState() == ProcessInstanceState.COMPLETED) {
      processInstanceFinished(instanceResult, processInstance, processInstanceVariables);
    }

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

  private void processInstanceFinished(
      InstanceResult instanceResult,
      ProcessInstance processInstance,
      Variables processInstanceVariables) {
    if (!processInstance.getParentProcessInstanceKey().equals(Constants.NONE_UUID)) {
      instanceResult.addContinuation(
          new ContinueFlowElementTrigger(
              processInstance.getParentProcessInstanceKey(),
              processInstance.getParentElementIdPath(),
              processInstance.getParentElementInstancePath(),
              Constants.NONE,
              variablesMapper.toDTO(processInstanceVariables)));
    }
  }
}
