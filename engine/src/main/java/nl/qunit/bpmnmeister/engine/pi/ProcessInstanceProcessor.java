package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CaffeineCache;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.engine.pi.processor.StateProcessor;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeStates;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKeyElementPair;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.jboss.logging.Logger;

public class ProcessInstanceProcessor
    implements Processor<ProcessInstanceKey, ProcessInstanceTrigger, Object, Object> {

  private static final Logger LOG = Logger.getLogger(ProcessInstanceProcessor.class);
  final ProcessorProvider processorProvider;
  private ProcessorContext<Object, Object> context;
  private KeyValueStore<ProcessInstanceKey, ProcessInstance> processInstanceStore;
  private KeyValueStore<ProcessInstanceKey, ProcessInstanceKeyElementPair>
      childProcessInstanceStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinition> processInstanceDefinitionStore;
  private final Cache processInstanceCache;
  private final Cache processInstanceDefinitionCache;

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
  }

  @Override
  public void process(Record<ProcessInstanceKey, ProcessInstanceTrigger> triggerRecord) {
    Instant start = Instant.now();
    ProcessInstanceTrigger trigger = triggerRecord.value();
    LOG.info("Processing trigger: " + trigger);
    ProcessInstance processInstance;
    ProcessDefinition definition;

    if (trigger instanceof StartNewProcessInstanceTrigger startNewProcessInstanceTrigger) {
      definition = startNewProcessInstanceTrigger.getProcessDefinition();
      ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(definition);
      processInstance =
          new ProcessInstance(
              startNewProcessInstanceTrigger.getParentElementId(),
              startNewProcessInstanceTrigger.getProcessInstanceKey(),
              startNewProcessInstanceTrigger.getParentProcessInstanceKey(),
              processDefinitionKey,
              FlowNodeStates.EMPTY,
              startNewProcessInstanceTrigger.getVariables(),
              ProcessInstanceState.START);
      storeProcessDefinition(definition);
      if (!startNewProcessInstanceTrigger
          .getParentProcessInstanceKey()
          .equals(ProcessInstanceKey.NONE)) {
        childProcessInstanceStore.put(
            startNewProcessInstanceTrigger.getProcessInstanceKey(),
            new ProcessInstanceKeyElementPair(
                startNewProcessInstanceTrigger.getParentProcessInstanceKey(),
                startNewProcessInstanceTrigger.getParentElementId()));
      }
    } else {
      processInstance = getProcessInstance(trigger.getProcessInstanceKey());
      definition = getProcessInstanceDefinition(processInstance.getProcessDefinitionKey());
    }

    ProcessInstance updatedProcessInstance = trigger(processInstance, definition, trigger);
    updateStoredProcessInstanceInformation(updatedProcessInstance);

    context.forward(
        new Record<>(
            updatedProcessInstance.getProcessInstanceKey(),
            updatedProcessInstance,
            Instant.now().toEpochMilli()));

    Instant end = Instant.now();
    LOG.info("Processing took " + (end.toEpochMilli() - start.toEpochMilli()) + " ms");
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
  ProcessInstance getProcessInstance(ProcessInstanceKey key) {
    return processInstanceStore.get(key);
  }

  @CacheResult(cacheName = "process-instance-definition-cache")
  ProcessDefinition getProcessInstanceDefinition(ProcessDefinitionKey key) {
    return processInstanceDefinitionStore.get(key);
  }

  public ProcessInstance trigger(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ProcessInstanceTrigger trigger) {
    if (trigger instanceof TerminateTrigger terminateProcessInstanceTrigger) {
      return handleTerminate(processInstance, definition, terminateProcessInstanceTrigger);
    } else {
      return handleElementTrigger(processInstance, definition, trigger);
    }
  }

  private ProcessInstance handleTerminate(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      TerminateTrigger terminateProcessInstanceTrigger) {
    LOG.info(
        "Terminating process instance "
            + processInstance.getProcessInstanceKey()
            + " with trigger "
            + terminateProcessInstanceTrigger);
    // Terminate all child process instances
    try (KeyValueIterator<ProcessInstanceKey, ProcessInstanceKeyElementPair> all =
        childProcessInstanceStore.all()) {
      all.forEachRemaining(
          childToParentKeyValue -> {
            ProcessInstanceKey childKey = childToParentKeyValue.key;
            ProcessInstanceKeyElementPair parentKeyElementPair = childToParentKeyValue.value;

            if (parentKeyElementPair
                    .getProcessInstanceKey()
                    .equals(processInstance.getProcessInstanceKey())
                && (terminateProcessInstanceTrigger.getElementId().equals(Constants.NONE)
                    || terminateProcessInstanceTrigger
                        .getElementId()
                        .equals(parentKeyElementPair.getElementId()))) {
              context.forward(
                  new Record<>(
                      childKey,
                      new TerminateTrigger(childKey, Constants.NONE),
                      Instant.now().toEpochMilli()));
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
      return handleElementTrigger(processInstance, definition, terminateProcessInstanceTrigger);
    }
  }

  private ProcessInstance handleElementTrigger(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      ProcessInstanceTrigger trigger) {
    ProcessInstance newProcessInstance = processInstance;

    LOG.info(
        "Triggering process instance "
            + processInstance.getProcessInstanceKey()
            + " with trigger "
            + trigger);
    String elementId = trigger.getElementId();
    Optional<FlowNode> optFlowNode =
        definition.getDefinitions().getRootProcess().getFlowElements().getFlowNode(elementId);
    if (optFlowNode.isPresent()) {
      // Merge the variables from the process instance with the variables from the trigger
      Variables mergedVariables = processInstance.getVariables().merge(trigger.getVariables());

      FlowNode flowNode = optFlowNode.get();
      StateProcessor<? extends BaseElement, ? extends FlowNodeState> processor =
          processorProvider.getProcessor(flowNode);
      TriggerResult triggerResult =
          processor.trigger(trigger, processInstance, definition, flowNode, mergedVariables);

      if (!triggerResult.equals(TriggerResult.EMPTY)) {
        newProcessInstance =
            processTriggerResult(
                processInstance, definition, elementId, triggerResult, mergedVariables, flowNode);
      }
    } else {
      LOG.error("Flownode not found: " + trigger.getElementId());
    }
    return newProcessInstance;
  }

  private ProcessInstance processTriggerResult(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      String elementId,
      TriggerResult triggerResult,
      Variables mergedVariables,
      BaseElement flowElement) {
    ProcessInstance newProcessInstance;
    FlowNodeStates newFlowNodeStates =
        processInstance.getFlowNodeStates().put(elementId, triggerResult.getNewFlowNodeState());

    Variables variablesWithTriggerResult = mergedVariables.merge(triggerResult.getVariables());

    ProcessInstanceState newProcessInstanceState =
        determineNewProcessInstanceState(processInstance, newFlowNodeStates, triggerResult);

    List<ProcessInstanceTrigger> nextTriggers =
        processTriggerResultForwards(
            processInstance, definition, triggerResult, variablesWithTriggerResult, flowElement);

    newProcessInstance =
        new ProcessInstance(
            processInstance.getParentElementId(),
            processInstance.getProcessInstanceKey(),
            processInstance.getParentInstanceKey(),
            processInstance.getProcessDefinitionKey(),
            newFlowNodeStates,
            variablesWithTriggerResult,
            newProcessInstanceState);

    for (ProcessInstanceTrigger nextTrigger : nextTriggers) {

      if ((nextTrigger instanceof StartNewProcessInstanceTrigger)
          || (definition
                      .getDefinitions()
                      .getRootProcess()
                      .getFlowElements()
                      .getFlowElement(nextTrigger.getElementId())
                      .orElse(null)
                  instanceof Activity
              && !(nextTrigger instanceof TerminateTrigger))
          || !nextTrigger
              .getProcessInstanceKey()
              .equals(newProcessInstance.getProcessInstanceKey())) {
        context.forward(
            new Record<>(
                nextTrigger.getProcessInstanceKey(), nextTrigger, Instant.now().toEpochMilli()));
      } else {
        newProcessInstance = trigger(newProcessInstance, definition, nextTrigger);
      }
    }
    return newProcessInstance;
  }

  private List<ProcessInstanceTrigger> processTriggerResultForwards(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      TriggerResult triggerResult,
      Variables variables,
      BaseElement flowElement) {
    triggerResult
        .getExternalTasks()
        .forEach(
            externalTaskId -> {
              LOG.info("Trigger external task: " + externalTaskId);
              ExternalTaskTrigger newExternalTaskTrigger =
                  new ExternalTaskTrigger(
                      processInstance.getProcessInstanceKey(),
                      ProcessDefinitionKey.of(definition),
                      externalTaskId,
                      variables);
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

    List<ProcessInstanceTrigger> nextTriggers = new ArrayList<>();

    triggerResult
        .getNewProcessInstanceTriggers()
        .forEach(
            newProcessInstanceTrigger -> {
              LOG.info("Trigger new process instance: " + newProcessInstanceTrigger);
              nextTriggers.add(newProcessInstanceTrigger);
            });

    triggerResult
        .getNewActiveFlows()
        .forEach(
            flowId -> {
              LOG.info("Trigger flow: " + flowId);
              SequenceFlow flow =
                  (SequenceFlow)
                      definition
                          .getDefinitions()
                          .getRootProcess()
                          .getFlowElements()
                          .getFlowElement(flowId)
                          .orElseThrow();
              FlowElementTrigger newTrigger =
                  new FlowElementTrigger(
                      processInstance.getProcessInstanceKey(),
                      flow.getTarget(),
                      flow.getId(),
                      variables);
              nextTriggers.add(newTrigger);
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
    return nextTriggers;
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
