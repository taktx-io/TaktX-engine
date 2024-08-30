package nl.qunit.bpmnmeister.engine.pi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.FLowNodeInstanceProcessor;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessInstanceProcessorProvider;
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

  private KeyValueStore<UUID, ProcessInstanceDTO> processInstanceStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> processInstanceDefinitionStore;
  private ProcessorContext<Object, Object> context;


  public ProcessInstanceProcessor2(
      DefinitionMapper definitionMapper,
      ProcessInstanceMapper instanceMapper,
      VariablesMapper variablesMapper,
      ProcessInstanceProcessorProvider processInstanceProcessorProvider,
      Forwarder forwarder) {
    this.definitionMapper = definitionMapper;
    this.instanceMapper = instanceMapper;
    this.variablesMapper = variablesMapper;
    this.processInstanceProcessorProvider = processInstanceProcessorProvider;
    this.forwarder = forwarder;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.processInstanceStore = context.getStateStore(Stores.PROCESS_INSTANCE_STORE_NAME);
    this.processInstanceDefinitionStore =
        context.getStateStore(Stores.PROCESS_INSTANCE_DEFINITION_STORE_NAME);
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTrigger2> triggerRecord) {
    ProcessInstanceTrigger2 trigger = triggerRecord.value();

    if (trigger instanceof StartNewProcessInstanceTrigger2 startNewProcessInstanceTrigger) {
      handleStartNewProcessInstance(startNewProcessInstanceTrigger);
    } else if (trigger instanceof ContinueFlowElementTrigger2 continueFlowElementTrigger2) {
      handleContinue(continueFlowElementTrigger2);
    } else {
      throw new IllegalArgumentException("Unknown trigger type: " + trigger.getClass());
    }
  }

  public void handleStartNewProcessInstance(StartNewProcessInstanceTrigger2 startNewProcessInstanceTrigger) {
    ProcessDefinitionDTO definitionDTO = startNewProcessInstanceTrigger.getProcessDefinition();
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(definitionDTO);
    processInstanceDefinitionStore.putIfAbsent(processDefinitionKey, definitionDTO);

    FlowElements2 flowElements =
        definitionMapper.getFlowElements(
            definitionDTO.getDefinitions().getRootProcess().getFlowElements());

    FlowNodeStates2 flowNodeStates = new FlowNodeStates2();

    ProcessInstance2 processInstance =
        new ProcessInstance2(
            startNewProcessInstanceTrigger.getProcessInstanceKey(),
            processDefinitionKey,
            flowNodeStates);

    FlowNode2 flowNode = flowElements.getStartNode(startNewProcessInstanceTrigger.getElementId());
    FLowNodeInstance flowNodeInstance = flowNode.newInstance();
    flowNodeStates.putInstance(flowNodeInstance);

    FLowNodeInstanceProcessor processor =
        processInstanceProcessorProvider.getProcessor(flowNode);

    InstanceResult instanceResult = processor.processStart(flowElements, flowNode, flowNodeInstance);

    continueNewInstances(instanceResult, flowNodeStates, flowElements, flowNode, processInstance,
        processDefinitionKey);
  }

  public void handleContinue (ContinueFlowElementTrigger2 trigger) {
    ProcessInstanceDTO processInstanceDTO =
        processInstanceStore.get(trigger.getProcessInstanceKey());
    if (processInstanceDTO != null) {
      ProcessDefinitionDTO processDefinitionDTO =
          processInstanceDefinitionStore.get(processInstanceDTO.getProcessDefinitionKey());
      if (processDefinitionDTO != null) {
        FlowElements2 flowElements =
            definitionMapper.getFlowElements(
                processDefinitionDTO.getDefinitions().getRootProcess().getFlowElements());

        ProcessInstance2 processInstance = instanceMapper.map(processInstanceDTO);
        FlowNode2 flowNode = flowElements.getFlowNode(trigger.getElementId()).get();
        FlowNodeStates2 flowNodeStates = processInstance.getFlowNodeStates();
        FLowNodeInstance flowNodeInstance = flowNodeStates.get(trigger.getElementInstanceId());

        FLowNodeInstanceProcessor processor =
            processInstanceProcessorProvider.getProcessor(flowNode);

        InstanceResult instanceResult =
            processor.processContinue(
                flowElements,
                flowNode,
                flowNodeInstance,
                trigger,
                Variables2.empty());

        continueNewInstances(instanceResult, flowNodeStates, flowElements, flowNode, processInstance,
            processInstanceDTO.getProcessDefinitionKey());
      }
    }
  }

  protected void continueNewInstances(InstanceResult instanceResult, FlowNodeStates2 flowNodeStates,
      FlowElements2 flowElements, FlowNode2 flowNode, ProcessInstance2 processInstance,
      ProcessDefinitionKey processDefinitionKey) {
    while (instanceResult.hasNewFlowNodeInstances()) {
      instanceResult =
          processInstanceResult(flowNodeStates, instanceResult, flowElements, flowNode);
    }

    determineImplicitCompletedState(flowNodeStates);

    ProcessInstanceDTO piDto = instanceMapper.map(processInstance);

    processInstanceStore.put(processInstance.getProcessInstanceKey(), piDto);

    forwarder.forward(
        context,
        instanceResult,
        processDefinitionKey,
        processInstance);

    context.forward(new Record<>(processInstance.getProcessInstanceKey(), new ProcessInstanceUpdate(piDto,
        VariablesDTO.empty()), Instant.now().toEpochMilli()));
  }

  private void determineImplicitCompletedState(FlowNodeStates2 flowNodeStates) {
    if (flowNodeStates.allMatch(FLowNodeInstance::isNotAwaiting)) {
      flowNodeStates.setState(ProcessInstanceState.COMPLETED);
    }
  }

  private InstanceResult processInstanceResult(
      FlowNodeStates2 flowNodeStates2,
      InstanceResult instanceResult,
      FlowElements2 flowElements,
      FlowNode2 flowNode) {
    InstanceResult newInstanceResult = new InstanceResult();
    List<FLowNodeInstance> newFlowNodeInstances = instanceResult.getNewFlowNodeInstances();
    for (FLowNodeInstance instance : newFlowNodeInstances) {
      flowNodeStates2.putInstance(instance);
      FlowNode2 node = flowElements.getFlowNode(instance.getElementId()).get();
      FLowNodeInstanceProcessor processor =
          processInstanceProcessorProvider.getProcessor(node);
      InstanceResult subInstanceResult =
          processor.processStart(flowElements, node, instance);
      newInstanceResult.merge(subInstanceResult);
    }
    return newInstanceResult;
  }

}
