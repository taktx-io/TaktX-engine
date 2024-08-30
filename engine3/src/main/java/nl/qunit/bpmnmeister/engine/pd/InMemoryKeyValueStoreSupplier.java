package nl.qunit.bpmnmeister.engine.pd;

import static org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

@Alternative
@ApplicationScoped
public class InMemoryKeyValueStoreSupplier implements KeyValueStoreSupplier {

  @Override
  public KeyValueBytesStoreSupplier get(String processInstanceStoreName) {
    return inMemoryKeyValueStore(processInstanceStoreName);
  }
}
