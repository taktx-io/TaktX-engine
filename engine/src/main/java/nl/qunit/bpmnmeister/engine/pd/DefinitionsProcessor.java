package nl.qunit.bpmnmeister.engine.pd;

import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class DefinitionsProcessor
    implements Processor<String, Definitions, ProcessDefinitionKey, ProcessDefinition> {

  private ProcessorContext<ProcessDefinitionKey, ProcessDefinition> context;
  private KeyValueStore<String, Integer> definitionCountByIdStore;
  private KeyValueStore<String, Definitions> xmlByHashStore;
  private KeyValueStore<ProcessDefinitionKey, ProcessDefinition> processDefinitionStore;

  @Override
  public void init(ProcessorContext<ProcessDefinitionKey, ProcessDefinition> context) {
    this.context = context;
    this.definitionCountByIdStore = context.getStateStore(Stores.DEFINITION_COUNT_BY_ID_STORE_NAME);
    this.processDefinitionStore = context.getStateStore(Stores.PROCESS_DEFINITION_STORE_NAME);
    this.xmlByHashStore = context.getStateStore(Stores.XML_BY_HASH_STORE_NAME);
  }

  @Override
  public void process(Record<String, Definitions> record) {
    String definitionId = record.key();
    Definitions definitions = record.value();
    String hash = definitions.getDefinitionsKey().getHash();
    ProcessDefinition processDefinition;
    if (xmlByHashStore.get(hash) != null) {
      // known hash, do not store but forward as existing processdefinition
      int version = definitionCountByIdStore.get(definitionId);
      ProcessDefinitionKey key = new ProcessDefinitionKey(definitionId, version);
      processDefinition = processDefinitionStore.get(key);
    } else {
      // unknwn hash, store and forward as new process definition
      xmlByHashStore.put(hash, definitions);
      Integer existingVersion = definitionCountByIdStore.get(definitionId);
      if (existingVersion == null) {
        existingVersion = 0;
      }
      int newVersion = existingVersion + 1;
      definitionCountByIdStore.put(definitionId, newVersion);
      processDefinition = new ProcessDefinition(definitions, newVersion);
      ProcessDefinitionKey key = new ProcessDefinitionKey(definitionId, newVersion);
      processDefinitionStore.put(key, processDefinition);
    }
    context.forward(
        new Record<>(
            new ProcessDefinitionKey(definitionId, processDefinition.getVersion()),
            processDefinition,
            record.timestamp()));
  }
}
