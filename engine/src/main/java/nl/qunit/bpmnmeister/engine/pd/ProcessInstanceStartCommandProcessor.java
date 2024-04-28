package nl.qunit.bpmnmeister.engine.pd;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartCommand;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ProcessInstanceStartCommandProcessor
    implements Processor<String, StartCommand, ProcessInstanceKey, ProcessInstanceTrigger> {
  private ProcessorContext<ProcessInstanceKey, ProcessInstanceTrigger> context;
  private KeyValueStore<String, Integer> definitionCountByIdStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinition> processDefinitionStore;

  @Override
  public void init(ProcessorContext<ProcessInstanceKey, ProcessInstanceTrigger> context) {
    this.context = context;
    this.definitionCountByIdStore = context.getStateStore(Stores.DEFINITION_COUNT_BY_ID_STORE_NAME);
    this.processDefinitionStore = context.getStateStore(Stores.PROCESS_DEFINITION_STORE_NAME);
  }

  @Override
  public void process(Record<String, StartCommand> startCommandRecord) {}
}
