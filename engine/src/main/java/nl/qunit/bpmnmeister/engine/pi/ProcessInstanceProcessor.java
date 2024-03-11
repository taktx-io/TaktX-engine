package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.engine.pi.processor.StateProcessor;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
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

  public ProcessInstanceProcessor(ProcessorProvider processorProvider) {
    this.processorProvider = processorProvider;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.processInstanceStore = context.getStateStore(Stores.PROCESS_INSTANCE_STORE_NAME);
  }

  @Override
  public void process(Record<ProcessInstanceKey, ProcessInstanceTrigger> triggerRecord) {
    Instant start = Instant.now();
    ProcessInstanceTrigger processInstanceTrigger = triggerRecord.value();
    ProcessInstance processInstance;
    if (processInstanceTrigger.getProcessDefinition() != null) {
      // When processDefinition is not null we need to instantiate a new process instance
      processInstance =
          new ProcessInstance(
              processInstanceTrigger.getParentProcessInstanceKey(),
              processInstanceTrigger.getProcessInstanceKey(),
              processInstanceTrigger.getProcessDefinition(),
              new HashMap<>(),
              processInstanceTrigger.getVariables());
      processInstanceStore.put(processInstance.getProcessInstanceKey(), processInstance);
    } else {
      // processDefinition is null, we expect the process instance in the store
      processInstance = processInstanceStore.get(processInstanceTrigger.getProcessInstanceKey());
    }
    ProcessInstance updatedProcessInstance =
        trigger(
            processInstance,
            processInstanceTrigger,
            trigger ->
                context.forward(
                    new Record<>(
                        trigger.getProcessInstanceKey(), trigger, Instant.now().toEpochMilli())),
            externalTask ->
                context.forward(
                    new Record<>(
                        externalTask.getProcessInstanceKey(),
                        externalTask,
                        Instant.now().toEpochMilli())));
    processInstanceStore.put(processInstance.getProcessInstanceKey(), updatedProcessInstance);
    Instant end = Instant.now();
    LOG.info("Processing took " + (end.toEpochMilli() - start.toEpochMilli()) + " ms");
  }

  public ProcessInstance trigger(
      ProcessInstance processInstance,
      ProcessInstanceTrigger trigger,
      Consumer<ProcessInstanceTrigger> processInstanceTriggerConsumer,
      Consumer<ExternalTaskTrigger> externalTaskTriggerConsumer) {
    LOG.info(
        "Triggering process instance "
            + processInstance.getProcessInstanceKey()
            + " with trigger "
            + trigger);
    Optional<FlowElement> optFlowElement =
        processInstance
            .getProcessDefinition()
            .getDefinitions()
            .getFlowElement(trigger.getElementId());
    if (optFlowElement.isPresent()) {
      Map<BaseElementId, BpmnElementState> newElementStates =
          new HashMap<>(processInstance.getElementStates());
      LOG.info("Element states: " + newElementStates);

      // Merge the variables from the process instance with the variables from the trigger
      Map<String, JsonNode> newVariables = new HashMap<>();
      if (processInstance.getVariables() != null) {
        newVariables.putAll(processInstance.getVariables());
      }
      if (trigger.getVariables() != null) {
        newVariables.putAll(trigger.getVariables());
      }
      LOG.info("Variables: " + newElementStates);

      BaseElement flowElement = optFlowElement.get();
      StateProcessor<? extends BaseElement, ? extends BpmnElementState> processor =
          processorProvider.getProcessor(flowElement);
      BpmnElementState elementState = newElementStates.get(trigger.getElementId());
      if (elementState == null) {
        elementState = processor.initialState();
      }
      LOG.info("Trigger processor: " + processor);
      TriggerResult triggerResult =
          processor.trigger(trigger, processInstance, flowElement, elementState, newVariables);
      LOG.info("Trigger processor result: " + triggerResult);

      newElementStates.put(trigger.getElementId(), triggerResult.getNewElementState());
      newVariables.putAll(triggerResult.getVariables());

      triggerResult
          .getExternalTasks()
          .forEach(
              externalTaskId -> {
                LOG.info("Trigger external task: " + externalTaskId);
                externalTaskTriggerConsumer.accept(
                    new ExternalTaskTrigger(
                        processInstance.getProcessInstanceKey(),
                        ProcessDefinitionKey.of(processInstance.getProcessDefinition()),
                        externalTaskId,
                        processInstance.getVariables()));
              });

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
                            .getFlowElement(flowId)
                            .orElseThrow();
                if (flow.testCondition()) {
                  LOG.info("Flow condition is true, triggering activity: " + flow.getTarget());
                  processInstanceTriggerConsumer.accept(
                      new ProcessInstanceTrigger(
                          processInstance.getProcessInstanceKey(),
                          ProcessInstanceKey.NULL,
                          ProcessDefinition.NULL,
                          flow.getTarget(),
                          false,
                          flow.getId(),
                          processInstance.getVariables()));
                }
              });

      triggerResult
          .getNewProcessInstanceTriggers()
          .forEach(
              newProcessInstanceTrigger -> {
                LOG.info("Trigger new process instance: " + newProcessInstanceTrigger);
                processInstanceTriggerConsumer.accept(newProcessInstanceTrigger);
              });

      return new ProcessInstance(
          ProcessInstanceKey.NULL,
          processInstance.getProcessInstanceKey(),
          processInstance.getProcessDefinition(),
          newElementStates,
          newVariables);
    } else {
      LOG.error("Flow element not found: " + trigger.getElementId());
    }
    return processInstance;
  }
}
