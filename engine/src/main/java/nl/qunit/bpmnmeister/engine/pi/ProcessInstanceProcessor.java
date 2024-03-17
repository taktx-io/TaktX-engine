package nl.qunit.bpmnmeister.engine.pi;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.engine.pi.processor.StateProcessor;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pi.ElementStates;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Trigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.jboss.logging.Logger;

public class ProcessInstanceProcessor
    implements Processor<ProcessInstanceKey, Trigger, Object, Object> {
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
  public void process(Record<ProcessInstanceKey, Trigger> triggerRecord) {
    Instant start = Instant.now();
    Trigger trigger = triggerRecord.value();

    ProcessInstance processInstance =
        trigger.getProcessInstance(() -> processInstanceStore.get(trigger.getProcessInstanceKey()));

    ProcessInstance updatedProcessInstance =
        trigger(
            processInstance,
            trigger,
            flowElementTrigger ->
                context.forward(
                    new Record<>(
                        flowElementTrigger.getProcessInstanceKey(),
                        flowElementTrigger,
                        Instant.now().toEpochMilli())),
            externalTaskTrigger ->
                context.forward(
                    new Record<>(
                        externalTaskTrigger.getProcessInstanceKey(),
                        externalTaskTrigger,
                        Instant.now().toEpochMilli())));
    processInstanceStore.put(processInstance.getProcessInstanceKey(), updatedProcessInstance);
    Instant end = Instant.now();
    LOG.info("Processing took " + (end.toEpochMilli() - start.toEpochMilli()) + " ms");
  }

  public ProcessInstance trigger(
      ProcessInstance processInstance,
      Trigger trigger,
      Consumer<Trigger> processInstanceTriggerConsumer,
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
            .getRootProcess()
            .getFlowElements()
            .getFlowElement(trigger.getElementId());
    if (optFlowElement.isPresent()) {
      LOG.info("Element states: " + processInstance.getElementStates());

      // Merge the variables from the process instance with the variables from the trigger
      Variables mergedVariables = processInstance.getVariables().merge(trigger.getVariables());
      LOG.info("Variables: " + mergedVariables);

      BaseElement flowElement = optFlowElement.get();
      StateProcessor<? extends BaseElement, ? extends BpmnElementState> processor =
          processorProvider.getProcessor(flowElement);
      BpmnElementState elementState =
          processInstance.getElementStates().get(trigger.getElementId());
      if (elementState == null) {
        elementState = processor.initialState();
      }
      LOG.info("Trigger processor: " + processor);
      TriggerResult triggerResult =
          processor.trigger(trigger, processInstance, flowElement, elementState, mergedVariables);
      LOG.info("Trigger processor result: " + triggerResult);

      ElementStates newElementStates =
          processInstance
              .getElementStates()
              .put(trigger.getElementId(), triggerResult.getNewElementState());

      Variables variablesWithTriggerResult = mergedVariables.merge(triggerResult.getVariables());

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
                        variablesWithTriggerResult));
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
                            .getRootProcess()
                            .getFlowElements()
                            .getFlowElement(flowId)
                            .orElseThrow();
                if (flow.testCondition()) {
                  LOG.info("Flow condition is true, triggering activity: " + flow.getTarget());
                  processInstanceTriggerConsumer.accept(
                      new FlowElementTrigger(
                          processInstance.getProcessInstanceKey(),
                          ProcessInstanceKey.NONE,
                          flow.getTarget(),
                          flow.getId(),
                          variablesWithTriggerResult));
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
          processInstance.getParentProcessInstanceKey(),
          processInstance.getProcessInstanceKey(),
          processInstance.getProcessDefinition(),
          newElementStates,
          variablesWithTriggerResult);
    } else {
      LOG.error("Flow element not found: " + trigger.getElementId());
    }
    return processInstance;
  }
}
