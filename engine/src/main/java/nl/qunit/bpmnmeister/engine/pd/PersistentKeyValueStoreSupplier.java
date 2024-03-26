package nl.qunit.bpmnmeister.engine.pd;

import static org.apache.kafka.streams.state.Stores.persistentKeyValueStore;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

@ApplicationScoped
public class PersistentKeyValueStoreSupplier implements KeyValueStoreSupplier {

  @Override
  public KeyValueBytesStoreSupplier get(String processInstanceStoreName) {
    return persistentKeyValueStore(processInstanceStoreName);
  }
}
