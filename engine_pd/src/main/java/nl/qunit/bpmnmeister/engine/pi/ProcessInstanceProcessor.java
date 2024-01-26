package nl.qunit.bpmnmeister.engine.pi;

import java.time.Instant;
import java.util.*;
import nl.qunit.bpmnmeister.engine.pd.Stores;
import nl.qunit.bpmnmeister.engine.pi.processor.StateProcessor;
import nl.qunit.bpmnmeister.pd.model.FlowElement;
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
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
    if (processInstance == null) {
      // new Process instance, the trigger is expected to contain the process definition
      processInstance =
          new ProcessInstance(
              processInstanceTrigger.getProcessInstanceKey(),
              processInstanceTrigger.getProcessDefinition(),
              new HashMap<>());
    }
    Set<ProcessInstanceTrigger> triggers = trigger(processInstance, processInstanceTrigger);
    processInstanceStore.put(processInstance.getProcessInstanceKey(), processInstance);
    triggers.forEach(
        trigger -> {
          context.forward(
              new Record<>(trigger.getProcessInstanceKey(), trigger, Instant.now().toEpochMilli()));
        });
    Instant end = Instant.now();
    LOG.info("Processing took " + (end.toEpochMilli() - start.toEpochMilli()) + " ms");
  }

  public Set<ProcessInstanceTrigger> trigger(
      ProcessInstance processInstance, ProcessInstanceTrigger trigger) {

    Optional<FlowElement> optFlowElement =
        processInstance.getProcessDefinition().getFlowElement(trigger.getElementId());
    Set<ProcessInstanceTrigger> newTriggers = new HashSet<>();
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
          .getNewActiveFlows()
          .forEach(
              flowId -> {
                SequenceFlow flow =
                    (SequenceFlow)
                        processInstance.getProcessDefinition().getFlowElement(flowId).orElseThrow();
                if (flow.testCondition()) {
                  newTriggers.add(
                      new ProcessInstanceTrigger(
                          processInstance.getProcessInstanceKey(),
                          null,
                          flow.getTarget(),
                          flow.getId()));
                }
              });
    }
    return newTriggers;
  }
}
