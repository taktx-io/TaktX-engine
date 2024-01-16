package nl.qunit.bpmnmeister.engine.pd;

import static nl.qunit.bpmnmeister.engine.pd.Stores.UNIQUE_KEY_DEFINITIONS_STORE_NAME;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@ApplicationScoped
class StoreDefinitionsProcessor implements Processor<String, Definitions, String, Definitions> {
  private ProcessorContext<String, Definitions> context;
  private KeyValueStore<String, Definitions> stateStore;

  @Override
  public void init(ProcessorContext context) {
    // Access the state store
    this.stateStore = context.getStateStore(UNIQUE_KEY_DEFINITIONS_STORE_NAME);
    this.context = context;
  }

  @Override
  public void process(Record<String, Definitions> record) {
    String key = record.key();
    Definitions value = record.value();
    if (stateStore.get(key) == null) {
      // Key does not exist, store it
      stateStore.put(key, value);
      context.forward(record);
    } else {
      // Key already exists, skip processing
      System.out.println("Key already exists: " + key);
    }
  }
}
