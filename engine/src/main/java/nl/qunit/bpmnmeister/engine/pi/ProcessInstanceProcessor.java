package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CaffeineCache;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.engine.pi.processor.StateProcessor;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeStates;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKeyElementPair;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.jboss.logging.Logger;

public class ProcessInstanceProcessor
    implements Processor<UUID, ProcessInstanceTrigger, Object, Object> {

  private static final Logger LOG = Logger.getLogger(ProcessInstanceProcessor.class);
  final ProcessorProvider processorProvider;
  private ProcessorContext<Object, Object> context;
  private KeyValueStore<UUID, ProcessInstance> processInstanceStore;
  private KeyValueStore<UUID, ProcessInstanceKeyElementPair> childProcessInstanceStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinition> processInstanceDefinitionStore;
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
    this.childProcessInstanceStore =
        context.getStateStore(Stores.CHILD_PARENT_PROCESS_INSTANCE_KEY_STORE_NAME);
    this.variablesStore = context.getStateStore(Stores.VARIABLES_STORE_NAME);
  }

  @Override
  public void process(Record<UUID, ProcessInstanceTrigger> triggerRecord) {
    ProcessInstanceTrigger trigger = triggerRecord.value();

    processTrigger(trigger);
  }

  private void processTrigger(ProcessInstanceTrigger trigger) {
    ProcessInstance processInstance;
    ProcessDefinition definition;
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
              processDefinitionKey,
              FlowNodeStates.EMPTY,
              ProcessInstanceState.START);
      storeProcessDefinition(definition);
      if (!startNewProcessInstanceTrigger
          .getParentProcessInstanceKey()
          .equals(Constants.NONE_UUID)) {
        childProcessInstanceStore.put(
            startNewProcessInstanceTrigger.getProcessInstanceKey(),
            new ProcessInstanceKeyElementPair(
                startNewProcessInstanceTrigger.getParentProcessInstanceKey(),
                startNewProcessInstanceTrigger.getParentElementId()));
      }
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
    // When the process has finished, failed or terminated, delete the process instance also from
    // the
    // parent-child store
    if (updatedProcessInstance.getProcessInstanceState().isFinished()) {
      childProcessInstanceStore.delete(updatedProcessInstance.getProcessInstanceKey());
    }
  }

  private void storeProcessDefinition(ProcessDefinition definition) {
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
  ProcessDefinition getProcessInstanceDefinition(ProcessDefinitionKey key) {
    return processInstanceDefinitionStore.get(key);
  }

  public ProcessInstance trigger(
      ProcessInstance processInstance,
      ProcessDefinition definition,
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
      updateStoredProcessInstanceInformation(updatedProcessInstance);
      sendProcessInstanceUpdate(updatedProcessInstance, variables);

      if (!updatedProcessInstance.getProcessInstanceState().isFinished()
          && updatedProcessInstance
              .getFlowNodeStates()
              .getWithState(FlowNodeStateEnum.ACTIVE)
              .isEmpty()) {
        updatedProcessInstance =
            updatedProcessInstance.toBuilder()
                .processInstanceState(ProcessInstanceState.COMPLETED)
                .build();
        if (!updatedProcessInstance.getParentInstanceKey().equals(Constants.NONE_UUID)) {
          processTrigger(
              new StartFlowElementTrigger(
                  updatedProcessInstance.getParentInstanceKey(),
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
      ProcessDefinition definition,
      TerminateTrigger terminateProcessInstanceTrigger) {
    LOG.info(
        "Terminating process instance "
            + processInstance.getProcessInstanceKey()
            + " with trigger "
            + terminateProcessInstanceTrigger);
    // Terminate all child process instances
    try (KeyValueIterator<UUID, ProcessInstanceKeyElementPair> all =
        childProcessInstanceStore.all()) {
      all.forEachRemaining(
          childToParentKeyValue -> {
            UUID childKey = childToParentKeyValue.key;
            ProcessInstanceKeyElementPair parentKeyElementPair = childToParentKeyValue.value;

            if (parentKeyElementPair
                    .getProcessInstanceKey()
                    .equals(processInstance.getProcessInstanceKey())
                && (terminateProcessInstanceTrigger.getElementId().equals(Constants.NONE)
                    || terminateProcessInstanceTrigger
                        .getElementId()
                        .equals(parentKeyElementPair.getElementId()))) {
              processTrigger(new TerminateTrigger(childKey, Constants.NONE));
            }
          });
    }

    ProcessInstance updatedProcessInstance = processInstance;
    if (terminateProcessInstanceTrigger.getElementId().equals(Constants.NONE)) {
      // Terminate all elements in this process
      List<FlowNode> flowNodes =
          definition.getDefinitions().getRootProcess().getFlowElements().getFlowNodes();
      for (FlowNode flowNode : flowNodes) {
        updatedProcessInstance =
            handleElementTrigger(
                updatedProcessInstance,
                variables,
                definition,
                new TerminateTrigger(processInstance.getProcessInstanceKey(), flowNode.getId()));
      }
      updatedProcessInstance =
          updatedProcessInstance.toBuilder()
              .processInstanceState(ProcessInstanceState.TERMINATED)
              .build();
      return updatedProcessInstance;
    } else {
      // Terminate the element indicated in the trigger.
      return handleElementTrigger(
          processInstance, variables, definition, terminateProcessInstanceTrigger);
    }
  }

  private ProcessInstance handleElementTrigger(
      ProcessInstance processInstance,
      ScopedVars scopedVars,
      ProcessDefinition definition,
      ProcessInstanceTrigger trigger) {
    ProcessInstance updatedProcessInstance = processInstance;

    String elementId = trigger.getElementId();
    Optional<FlowNode> optFlowNode =
        definition.getDefinitions().getRootProcess().getFlowElements().getFlowNode(elementId);
    if (optFlowNode.isPresent()) {
      FlowNode flowNode = optFlowNode.get();
      StateProcessor<? extends BaseElement, ? extends FlowNodeState> processor =
          processorProvider.getProcessor(flowNode);
      TriggerResult triggerResult =
          processor.trigger(trigger, processInstance, definition, flowNode, scopedVars);

      if (!triggerResult.equals(TriggerResult.EMPTY)) {
        updatedProcessInstance =
            processTriggerResult(
                processInstance, definition, elementId, triggerResult, scopedVars, flowNode);
      }
    } else {
      LOG.error("Flownode not found: " + trigger.getElementId());
    }
    return updatedProcessInstance;
  }

  private ProcessInstance processTriggerResult(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      String elementId,
      TriggerResult triggerResult,
      ScopedVars variables,
      BaseElement flowElement) {
    ProcessInstance updatedProcessInstance;
    FlowNodeStates newFlowNodeStates =
        processInstance.getFlowNodeStates().put(elementId, triggerResult.getNewFlowNodeState());

    ProcessInstanceState newProcessInstanceState =
        determineNewProcessInstanceState(processInstance, newFlowNodeStates, triggerResult);

    processTriggerResultForwards(processInstance, definition, triggerResult, flowElement);

    updatedProcessInstance =
        new ProcessInstance(
            processInstance.getRootInstanceKey(),
            processInstance.getProcessInstanceKey(),
            processInstance.getParentInstanceKey(),
            processInstance.getParentElementId(),
            processInstance.getProcessDefinitionKey(),
            newFlowNodeStates,
            newProcessInstanceState);
    updateStoredProcessInstanceInformation(updatedProcessInstance);
    sendProcessInstanceUpdate(updatedProcessInstance, variables);

    for (ProcessInstanceTrigger nextTrigger : triggerResult.getProcessInstanceTriggers()) {

      if (nextTrigger instanceof StartNewProcessInstanceTrigger) {
        processTrigger(nextTrigger);
      } else {
        updatedProcessInstance =
            trigger(updatedProcessInstance, definition, nextTrigger, variables);
      }
    }
    return updatedProcessInstance;
  }

  private void processTriggerResultForwards(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      TriggerResult triggerResult,
      BaseElement flowElement) {
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
