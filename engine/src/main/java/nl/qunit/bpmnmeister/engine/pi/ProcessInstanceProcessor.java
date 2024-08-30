package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CaffeineCache;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.engine.pi.processor.StateProcessor;
import nl.qunit.bpmnmeister.pd.model.BaseElementDTO;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowElementsDTO;
import nl.qunit.bpmnmeister.pd.model.FlowNodeDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.SubProcessDTO;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeStates;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.jboss.logging.Logger;

public class ProcessInstanceProcessor
    implements Processor<UUID, ProcessInstanceTrigger, Object, Object> {

  private static final Logger LOG = Logger.getLogger(ProcessInstanceProcessor.class);
  final ProcessorProvider processorProvider;
  private ProcessorContext<Object, Object> context;
  private KeyValueStore<UUID, ProcessInstance> processInstanceStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> processInstanceDefinitionStore;
  private final Cache processInstanceCache;
  private final Cache processInstanceDefinitionCache;
  private KeyValueStore<UUID, VariablesParentPair> variablesStore;

  public ProcessInstanceProcessor(
      ProcessorProvider processorProvider,
      Cache processInstanceCache,
      Cache processInstanceDefinitionCache) {
    this.processorProvider = processorProvider;
    this.processInstanceCache = processInstanceCache;
    this.processInstanceDefinitionCache = processInstanceDefinitionCache;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.processInstanceStore = context.getStateStore(Stores.PROCESS_INSTANCE_STORE_NAME);
    this.processInstanceDefinitionStore =
        context.getStateStore(Stores.PROCESS_INSTANCE_DEFINITION_STORE_NAME);
    this.variablesStore = context.getStateStore(Stores.VARIABLES_STORE_NAME);
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTrigger> triggerRecord) {
    ProcessInstanceTrigger trigger = triggerRecord.value();

    processTrigger(trigger);
  }

  private void processTrigger(ProcessInstanceTrigger trigger) {
    ProcessInstance processInstance;
    ProcessDefinitionDTO definition;
    KeyValueStoreScopedVars scopedVars = new KeyValueStoreScopedVars(variablesStore);
    if (trigger instanceof StartNewProcessInstanceTrigger startNewProcessInstanceTrigger) {
      definition = startNewProcessInstanceTrigger.getProcessDefinition();
      ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(definition);
      scopedVars.push(
          startNewProcessInstanceTrigger.getProcessInstanceKey(),
          startNewProcessInstanceTrigger.getParentProcessInstanceKey(),
          startNewProcessInstanceTrigger.getVariables());
      processInstance =
          new ProcessInstance(
              startNewProcessInstanceTrigger.getRootInstanceKey(),
              startNewProcessInstanceTrigger.getProcessInstanceKey(),
              startNewProcessInstanceTrigger.getParentProcessInstanceKey(),
              startNewProcessInstanceTrigger.getParentElementId(),
              startNewProcessInstanceTrigger.getSourceInstanceId(),
              processDefinitionKey,
              FlowNodeStates.EMPTY,
              ProcessInstanceState.START);
      storeProcessDefinition(definition);
    } else {
      processInstance = getStoredProcessInstance(trigger.getProcessInstanceKey());
      definition = getProcessInstanceDefinition(processInstance.getProcessDefinitionKey());
    }

    ProcessInstance updatedProcessInstance =
        trigger(processInstance, definition, trigger, scopedVars);
    updateStoredProcessInstanceInformation(updatedProcessInstance);
    sendProcessInstanceUpdate(updatedProcessInstance, scopedVars);
  }

  private void sendProcessInstanceUpdate(
      ProcessInstance updatedProcessInstance, ScopedVars scopedVars) {
    ProcessInstanceUpdate processInstanceUpdate =
        new ProcessInstanceUpdate(updatedProcessInstance, scopedVars.getCurrentScopeVariables());
    context.forward(
        new Record<>(
            processInstanceUpdate.getProcessInstanceKey(),
            processInstanceUpdate,
            Instant.now().toEpochMilli()));
  }

  private void updateStoredProcessInstanceInformation(ProcessInstance updatedProcessInstance) {
    processInstanceCache
        .as(CaffeineCache.class)
        .put(
            updatedProcessInstance.getProcessInstanceKey(),
            CompletableFuture.completedFuture(updatedProcessInstance));
    processInstanceStore.put(
        updatedProcessInstance.getProcessInstanceKey(), updatedProcessInstance);
  }

  private void storeProcessDefinition(ProcessDefinitionDTO definition) {
    processInstanceDefinitionCache
        .as(CaffeineCache.class)
        .put(ProcessDefinitionKey.of(definition), CompletableFuture.completedFuture(definition));
    processInstanceDefinitionStore.put(ProcessDefinitionKey.of(definition), definition);
  }

  @CacheResult(cacheName = "process-instance-cache")
  ProcessInstance getStoredProcessInstance(UUID key) {
    return processInstanceStore.get(key);
  }

  @CacheResult(cacheName = "process-instance-definition-cache")
  ProcessDefinitionDTO getProcessInstanceDefinition(ProcessDefinitionKey key) {
    return processInstanceDefinitionStore.get(key);
  }

  public ProcessInstance trigger(
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      ProcessInstanceTrigger trigger,
      ScopedVars variables) {
    variables.select(trigger.getProcessInstanceKey());
    variables.merge(trigger.getVariables());
    ProcessInstance updatedProcessInstance;
    if (trigger instanceof TerminateTrigger terminateProcessInstanceTrigger) {
      updatedProcessInstance =
          handleTerminate(processInstance, variables, definition, terminateProcessInstanceTrigger);
      updateStoredProcessInstanceInformation(updatedProcessInstance);
      sendProcessInstanceUpdate(updatedProcessInstance, variables);

    } else {
      updatedProcessInstance =
          handleElementTrigger(processInstance, variables, definition, trigger);
      if (!updatedProcessInstance.getProcessInstanceState().isFinished()
          && updatedProcessInstance
              .getFlowNodeStates()
              .getWithState(FlowNodeStateEnum.WAITING)
              .isEmpty()) {
        updatedProcessInstance =
            updatedProcessInstance.toBuilder()
                .processInstanceState(ProcessInstanceState.COMPLETED)
                .build();
        updateStoredProcessInstanceInformation(updatedProcessInstance);
        sendProcessInstanceUpdate(updatedProcessInstance, variables);

        if (!updatedProcessInstance.getParentInstanceKey().equals(Constants.NONE_UUID)) {
          processTrigger(
              new ContinueFlowElementTrigger(
                  updatedProcessInstance.getParentInstanceKey(),
                  updatedProcessInstance.getParentElementInstanceId1(),
                  updatedProcessInstance.getParentElementId(),
                  Constants.NONE,
                  variables.getCurrentScopeVariables()));
        }
      }
    }
    return updatedProcessInstance;
  }

  private ProcessInstance handleTerminate(
      ProcessInstance processInstance,
      ScopedVars variables,
      ProcessDefinitionDTO definition,
      TerminateTrigger terminateProcessInstanceTrigger) {
    LOG.info(
        "Terminating process instance "
            + processInstance.getProcessInstanceKey()
            + " with trigger "
            + terminateProcessInstanceTrigger);
    ProcessInstance updatedProcessInstance = processInstance;
    if (terminateProcessInstanceTrigger.getElementInstanceId().equals(Constants.NONE_UUID)) {
      // Terminate all elements in this process
      for (FlowNodeStateDTO flowNodeState :
          processInstance.getFlowNodeStates().getElementStateMap().values()) {
        updatedProcessInstance =
            handleElementTrigger(
                updatedProcessInstance,
                variables,
                definition,
                new TerminateTrigger(
                    processInstance.getProcessInstanceKey(),
                    flowNodeState.getElementId(),
                    flowNodeState.getElementInstanceId()));
      }
      updatedProcessInstance =
          updatedProcessInstance.toBuilder()
              .processInstanceState(ProcessInstanceState.TERMINATED)
              .build();
      return updatedProcessInstance;
    } else {
      // Terminate the element indicated in the trigger.
      Optional<FlowNodeStateDTO> optFlowNodeState =
          processInstance
              .getFlowNodeStates()
              .get(terminateProcessInstanceTrigger.getElementInstanceId());
      if (optFlowNodeState.isPresent()) {
        String elementId = optFlowNodeState.get().getElementId();
        Optional<FlowNodeDTO> optFlowNode = getFlowNode(definition, elementId);
        if (optFlowNode.isPresent()) {
          FlowNodeDTO flowNode = optFlowNode.get();
          StateProcessor<? extends BaseElementDTO, ? extends FlowNodeStateDTO> processor =
              processorProvider.getProcessor(flowNode);
          TriggerResult triggerResult =
              processor.trigger(
                  terminateProcessInstanceTrigger,
                  processInstance,
                  definition,
                  flowNode,
                  variables);

          if (!triggerResult.equals(TriggerResult.EMPTY)) {
            updatedProcessInstance =
                processTriggerResult(
                    processInstance, definition, triggerResult, variables, flowNode);
          }
        }
      }
      return updatedProcessInstance;
    }
  }

  private ProcessInstance handleElementTrigger(
      ProcessInstance processInstance,
      ScopedVars scopedVars,
      ProcessDefinitionDTO definition,
      ProcessInstanceTrigger trigger) {
    ProcessInstance updatedProcessInstance = processInstance;

    String elementId = trigger.getElementId();
    Optional<FlowNodeDTO> optFlowNode = getFlowNode(definition, elementId);
    if (optFlowNode.isPresent()) {
      FlowNodeDTO flowNode = optFlowNode.get();
      StateProcessor<? extends BaseElementDTO, ? extends FlowNodeStateDTO> processor =
          processorProvider.getProcessor(flowNode);
      TriggerResult triggerResult =
          processor.trigger(trigger, processInstance, definition, flowNode, scopedVars);

      if (!triggerResult.equals(TriggerResult.EMPTY)) {
        updatedProcessInstance =
            processTriggerResult(processInstance, definition, triggerResult, scopedVars, flowNode);
      }
    } else {
      LOG.error("Flownode not found: " + trigger.getElementId());
    }
    return updatedProcessInstance;
  }

  private static Optional<FlowNodeDTO> getFlowNode(ProcessDefinitionDTO definition, String elementId) {
    String[] splittedElementIds = elementId.split("/");
    FlowElementsDTO flowElements = definition.getDefinitions().getRootProcess().getFlowElements();

    Optional<FlowNodeDTO> optFlowNode = Optional.empty();
    for(String splittedElementId : splittedElementIds) {
      optFlowNode = flowElements.getFlowNode(splittedElementId);
      if(optFlowNode.isPresent() && optFlowNode.get() instanceof SubProcessDTO subProcess) {
        flowElements = subProcess.getElements();
      }
    }
    return optFlowNode;
  }

  private ProcessInstance processTriggerResult(
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      TriggerResult triggerResult,
      ScopedVars variables,
      BaseElementDTO flowElement) {
    ProcessInstance updatedProcessInstance;
    FlowNodeStates newFlowNodeStates =
        processInstance.getFlowNodeStates().putAll(triggerResult.getNewFlowNodeStates());

    ProcessInstanceState newProcessInstanceState =
        determineNewProcessInstanceState(processInstance, newFlowNodeStates, triggerResult);

    processTriggerResultForwards(processInstance, definition, triggerResult, flowElement);

    updatedProcessInstance =
        new ProcessInstance(
            processInstance.getRootInstanceKey(),
            processInstance.getProcessInstanceKey(),
            processInstance.getParentInstanceKey(),
            processInstance.getParentElementId(),
            processInstance.getParentElementInstanceId1(),
            processInstance.getProcessDefinitionKey(),
            newFlowNodeStates,
            newProcessInstanceState);
    updateStoredProcessInstanceInformation(updatedProcessInstance);
    sendProcessInstanceUpdate(updatedProcessInstance, variables);

    for (ProcessInstanceTrigger nextTrigger : triggerResult.getProcessInstanceTriggers()) {

      if (nextTrigger instanceof StartNewProcessInstanceTrigger
          || triggerIsTerminateProcessInstance(nextTrigger)) {
        processTrigger(nextTrigger);
      } else {
        updatedProcessInstance =
            trigger(updatedProcessInstance, definition, nextTrigger, variables);
      }
    }
    return updatedProcessInstance;
  }

  private boolean triggerIsTerminateProcessInstance(ProcessInstanceTrigger nextTrigger) {
    if (nextTrigger instanceof TerminateTrigger terminateTrigger) {
      return terminateTrigger.getElementInstanceId().equals(Constants.NONE_UUID);
    }
    return false;
  }

  private void processTriggerResultForwards(
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      TriggerResult triggerResult,
      BaseElementDTO flowElement) {
    triggerResult
        .getExternalTasks()
        .forEach(
            externalTask -> {
              LOG.info("Trigger external task: " + externalTask);
              ExternalTaskTrigger newExternalTaskTrigger =
                  new ExternalTaskTrigger(
                      processInstance.getRootInstanceKey(),
                      processInstance.getProcessInstanceKey(),
                      ProcessDefinitionKey.of(definition),
                      externalTask.getExternalTaskId(),
                      externalTask.getElementId(),
                      externalTask.getElementInstanceId(),
                      externalTask.getVariables());
              context.forward(
                  new Record<>(
                      newExternalTaskTrigger.getProcessInstanceKey(),
                      newExternalTaskTrigger,
                      Instant.now().toEpochMilli()));
            });

    triggerResult
        .getMessageSchedulers()
        .forEach(
            messageScheduler -> {
              LOG.info("Trigger scheduled message: " + messageScheduler);
              ScheduleKey scheduleKey =
                  new ScheduleKey(
                      ProcessDefinitionKey.of(definition),
                      processInstance.getProcessInstanceKey(),
                      messageScheduler.getScheduleType(),
                      flowElement.getId(),
                      "");
              context.forward(
                  new Record<>(scheduleKey, messageScheduler, Instant.now().toEpochMilli()));
            });

    triggerResult
        .getCancelSchedules()
        .forEach(
            canceledScheduleKey -> {
              LOG.info("cancel schedule: ");
              context.forward(
                  new Record<>(canceledScheduleKey, null, Instant.now().toEpochMilli()));
            });

    triggerResult
        .getNewStartCommands()
        .forEach(
            startCommand ->
                context.forward(
                    new Record<>(
                        startCommand.getProcessDefinitionId(),
                        startCommand,
                        Instant.now().toEpochMilli())));

    triggerResult
        .getNewMessageSubscriptions()
        .forEach(
            messageSubscription -> {
              LOG.info("Trigger new message subscription: " + messageSubscription);
              context.forward(
                  new Record<>(
                      messageSubscription.getKey(),
                      messageSubscription,
                      Instant.now().toEpochMilli()));
            });
  }

  private ProcessInstanceState determineNewProcessInstanceState(
      ProcessInstance processInstance,
      FlowNodeStates newFlowNodeStates,
      TriggerResult triggerResult) {
    ProcessInstanceState newProcessInstanceState =
        triggerResult.getThrowingEvent().process(processInstance, newFlowNodeStates);
    if (newProcessInstanceState.isFinished()) {
      processInstanceCache.invalidate(processInstance.getProcessInstanceKey());
    }
    return newProcessInstanceState;
  }
}
