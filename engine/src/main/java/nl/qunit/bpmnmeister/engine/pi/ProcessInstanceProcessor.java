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
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ElementStates;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.jboss.logging.Logger;

public class ProcessInstanceProcessor
    implements Processor<ProcessInstanceKey, ProcessInstanceTrigger, Object, Object> {

  private static final Logger LOG = Logger.getLogger(ProcessInstanceProcessor.class);
  final ProcessorProvider processorProvider;
  private ProcessorContext<Object, Object> context;
  private KeyValueStore<ProcessInstanceKey, ProcessInstance> processInstanceStore;
  private final Cache processInstanceCache;

  public ProcessInstanceProcessor(ProcessorProvider processorProvider, Cache processInstanceCache) {
    this.processorProvider = processorProvider;
    this.processInstanceCache = processInstanceCache;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.processInstanceStore = context.getStateStore(Stores.PROCESS_INSTANCE_STORE_NAME);
  }

  @Override
  public void process(Record<ProcessInstanceKey, ProcessInstanceTrigger> triggerRecord) {
    Instant start = Instant.now();
    ProcessInstanceTrigger trigger = triggerRecord.value();
    LOG.info("Processing trigger: " + trigger);
    ProcessInstance processInstance;
    if (trigger.getProcessDefinition().equals(ProcessDefinition.NONE)) {
      processInstance = getProcessInstance(trigger.getProcessInstanceKey());
    } else {
      processInstance =
          new ProcessInstance(
              trigger.getParentProcessInstanceKey(),
              trigger.getParentElementId(),
              trigger.getProcessInstanceKey(),
              trigger.getProcessDefinition(),
              ElementStates.EMPTY,
              trigger.getVariables(),
              ProcessInstanceState.START);
    }

    ProcessInstance updatedProcessInstance = trigger(processInstance, trigger);
    storeProcessInstance(updatedProcessInstance);

    context.forward(
        new Record<>(
            updatedProcessInstance.getProcessInstanceKey(),
            updatedProcessInstance,
            Instant.now().toEpochMilli()));

    Instant end = Instant.now();
    LOG.info("Processing took " + (end.toEpochMilli() - start.toEpochMilli()) + " ms");
  }

  private void storeProcessInstance(ProcessInstance updatedProcessInstance) {
    processInstanceCache
        .as(CaffeineCache.class)
        .put(
            updatedProcessInstance.getProcessInstanceKey(),
            CompletableFuture.completedFuture(updatedProcessInstance));
    processInstanceStore.put(
        updatedProcessInstance.getProcessInstanceKey(), updatedProcessInstance);
  }

  @CacheResult(cacheName = "process-instance-cache")
  ProcessInstance getProcessInstance(ProcessInstanceKey key) {
    return processInstanceStore.get(key);
  }

  public ProcessInstance trigger(ProcessInstance processInstance, ProcessInstanceTrigger trigger) {
    ProcessInstance newProcessInstance = processInstance;

    LOG.info(
        "Triggering process instance "
            + processInstance.getProcessInstanceKey()
            + " with trigger "
            + trigger);
    String elementId =
        trigger.getElementId().equals(Constants.NONE)
            ? trigger.getParentElementId()
            : trigger.getElementId();
    Optional<FlowElement> optFlowElement =
        processInstance
            .getProcessDefinition()
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getFlowElement(elementId);
    if (optFlowElement.isPresent()) {
      LOG.info("Element states: " + processInstance.getElementStates());

      // Merge the variables from the process instance with the variables from the trigger
      Variables mergedVariables = processInstance.getVariables().merge(trigger.getVariables());
      LOG.info("Variables: " + mergedVariables);

      BaseElement flowElement = optFlowElement.get();
      StateProcessor<? extends BaseElement, ? extends BpmnElementState> processor =
          processorProvider.getProcessor(flowElement);
      BpmnElementState elementState = processInstance.getElementStates().get(elementId);
      if (elementState == null) {
        elementState = processor.initialState();
      }
      LOG.info("Trigger processor: " + processor);
      TriggerResult triggerResult =
          processor.trigger(trigger, processInstance, flowElement, elementState, mergedVariables);
      LOG.info("Trigger processor result: " + triggerResult);

      ElementStates newElementStates =
          processInstance.getElementStates().put(elementId, triggerResult.getNewElementState());

      Variables variablesWithTriggerResult = mergedVariables.merge(triggerResult.getVariables());

      ProcessInstanceState newProcessInstanceState =
          determineNewProcessInstanceState(processInstance, triggerResult);

      triggerResult
          .getExternalTasks()
          .forEach(
              externalTaskId -> {
                LOG.info("Trigger external task: " + externalTaskId);
                ExternalTaskTrigger newExternalTaskTrigger =
                    new ExternalTaskTrigger(
                        processInstance.getProcessInstanceKey(),
                        ProcessDefinitionKey.of(processInstance.getProcessDefinition()),
                        externalTaskId,
                        variablesWithTriggerResult);
                context.forward(
                    new Record<>(
                        newExternalTaskTrigger.getProcessInstanceKey(),
                        newExternalTaskTrigger,
                        Instant.now().toEpochMilli()));
              });

      List<ProcessInstanceTrigger> nextTriggers = new ArrayList<>();
      triggerResult
          .getNewActiveFlows()
          .forEach(
              flowId -> {
                LOG.info("Trigger flow: " + flowId);
                SequenceFlow flow =
                    (SequenceFlow)
                        processInstance
                            .getProcessDefinition()
                            .getDefinitions()
                            .getRootProcess()
                            .getFlowElements()
                            .getFlowElement(flowId)
                            .orElseThrow();
                if (flow.testCondition()) {
                  LOG.info("Flow condition is true, triggering activity: " + flow.getTarget());
                  FlowElementTrigger newTrigger =
                      new FlowElementTrigger(
                          processInstance.getProcessInstanceKey(),
                          processInstance.getParentProcessInstanceKey(),
                          processInstance.getParentElementId(),
                          ProcessDefinition.NONE,
                          flow.getTarget(),
                          flow.getId(),
                          variablesWithTriggerResult);
                  nextTriggers.add(newTrigger);
                }
              });

      triggerResult
          .getNewProcessInstanceTriggers()
          .forEach(
              newProcessInstanceTrigger -> {
                LOG.info("Trigger new process instance: " + newProcessInstanceTrigger);
                nextTriggers.add(newProcessInstanceTrigger);
              });

      triggerResult
          .getNewStartCommands()
          .forEach(
              startCommand -> {
                context.forward(
                    new Record<>(
                        startCommand.getProcessDefinitionId(),
                        startCommand,
                        Instant.now().toEpochMilli()));
              });

      newProcessInstance =
          new ProcessInstance(
              processInstance.getParentProcessInstanceKey(),
              processInstance.getParentElementId(),
              processInstance.getProcessInstanceKey(),
              processInstance.getProcessDefinition(),
              newElementStates,
              variablesWithTriggerResult,
              newProcessInstanceState);

      for (ProcessInstanceTrigger nextTrigger : nextTriggers) {

        if (!nextTrigger.getProcessDefinition().equals(ProcessDefinition.NONE)
            || processInstance
                    .getProcessDefinition()
                    .getDefinitions()
                    .getRootProcess()
                    .getFlowElements()
                    .getFlowElement(nextTrigger.getElementId())
                    .orElse(null)
                instanceof Activity
            || !nextTrigger
                .getProcessInstanceKey()
                .equals(newProcessInstance.getProcessInstanceKey())) {
          context.forward(
              new Record<>(
                  nextTrigger.getProcessInstanceKey(), nextTrigger, Instant.now().toEpochMilli()));
        } else {
          newProcessInstance = trigger(newProcessInstance, nextTrigger);
        }
      }
    } else {
      LOG.error("Flow element not found: " + trigger.getElementId());
    }
    return newProcessInstance;
  }

  private ProcessInstanceState determineNewProcessInstanceState(
      ProcessInstance processInstance, TriggerResult triggerResult) {
    ProcessInstanceState newProcessInstanceState =
        triggerResult.getThrowingEvent().process(processInstance);
    if (newProcessInstanceState.isFinished()) {
      processInstanceCache.invalidate(processInstance.getProcessInstanceKey());
    }
    return newProcessInstanceState;
  }
}
