package nl.qunit.bpmnmeister.engine.pd;

import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

public interface KeyValueStoreSupplier {

  KeyValueBytesStoreSupplier get(Stores store);
}
