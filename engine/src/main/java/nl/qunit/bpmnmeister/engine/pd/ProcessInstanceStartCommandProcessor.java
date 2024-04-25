package nl.qunit.bpmnmeister.engine.pd;

import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceStartCommand;
import nl.qunit.bpmnmeister.pi.Trigger;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ProcessInstanceStartCommandProcessor
    implements Processor<String, ProcessInstanceStartCommand, ProcessInstanceKey, Trigger> {
  private ProcessorContext<ProcessInstanceKey, Trigger> context;
  private KeyValueStore<String, Integer> definitionCountByIdStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinition> processDefinitionStore;

  @Override
  public void init(ProcessorContext<ProcessInstanceKey, Trigger> context) {
    this.context = context;
    this.definitionCountByIdStore = context.getStateStore(Stores.DEFINITION_COUNT_BY_ID_STORE_NAME);
    this.processDefinitionStore = context.getStateStore(Stores.PROCESS_DEFINITION_STORE_NAME);
  }

  @Override
  public void process(Record<String, ProcessInstanceStartCommand> startCommandRecord) {
    Integer latestVersion = this.definitionCountByIdStore.get(startCommandRecord.key());
    if (latestVersion == null) {
      throw new IllegalStateException(
          "Process definition not found for key: " + startCommandRecord.key());
    }

    ProcessDefinition processDefinition =
        processDefinitionStore.get(
            new ProcessDefinitionKey(startCommandRecord.key(), latestVersion));
    ProcessInstanceKey processInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
    Trigger processInstanceTrigger =
        new FlowElementTrigger(
            processInstanceKey,
            ProcessInstanceKey.NONE,
            processDefinition,
            startCommandRecord.value().getElementId(),
            Constants.NONE,
            startCommandRecord.value().getVariables());
    context.forward(
        new Record<>(processInstanceKey, processInstanceTrigger, startCommandRecord.timestamp()));
  }
}
