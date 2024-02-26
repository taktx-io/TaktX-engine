package nl.qunit.bpmnmeister.engine.pi;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.StateProcessor;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
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
  public void process(Record<ProcessInstanceKey, ProcessInstanceTrigger> record) {
    Instant start = Instant.now();
    ProcessInstanceTrigger processInstanceTrigger = record.value();
    ProcessInstance processInstance =
        processInstanceStore.get(processInstanceTrigger.getProcessInstanceKey());
    trigger(
        processInstance,
        processInstanceTrigger,
        trigger -> {
          context.forward(
              new Record<>(trigger.getProcessInstanceKey(), trigger, Instant.now().toEpochMilli()));
        },
        externalTask -> {
          context.forward(
              new Record<>(
                  externalTask.getProcessInstanceKey(),
                  externalTask,
                  Instant.now().toEpochMilli()));
        });
    processInstanceStore.put(processInstance.getProcessInstanceKey(), processInstance);
    Instant end = Instant.now();
    LOG.info("Processing took " + (end.toEpochMilli() - start.toEpochMilli()) + " ms");
  }

  public void trigger(
      ProcessInstance processInstance,
      ProcessInstanceTrigger trigger,
      Consumer<ProcessInstanceTrigger> processInstanceTriggerConsumer,
      Consumer<ExternalTaskTrigger> externalTaskTriggerConsumer) {

    Optional<FlowElement> optFlowElement =
        processInstance.getProcessDefinition().getFlowElement(trigger.getElementId());
    if (optFlowElement.isPresent()) {
      FlowElement flowElement = optFlowElement.get();
      StateProcessor<?, ?> processor = processorProvider.getProcessor(flowElement);
      BpmnElementState elementState =
          processInstance.getElementStates().get(trigger.getElementId());
      if (elementState == null) {
        elementState = processor.initialState();
      }
      TriggerResult triggerResult =
          processor.trigger(
              trigger, processInstance.getProcessDefinition(), flowElement, elementState);
      processInstance
          .getElementStates()
          .put(trigger.getElementId(), triggerResult.getNewElementState());
      triggerResult
          .getExternalTasks()
          .forEach(
              externalTaskId -> {
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
                SequenceFlow flow =
                    (SequenceFlow)
                        processInstance.getProcessDefinition().getFlowElement(flowId).orElseThrow();
                if (flow.testCondition()) {
                  processInstanceTriggerConsumer.accept(
                      new ProcessInstanceTrigger(
                          processInstance.getProcessInstanceKey(),
                          flow.getTarget(),
                          flow.getId(),
                          processInstance.getVariables()));
                }
              });
    }
  }
}
