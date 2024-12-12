package com.flomaestro.engine.pd;

import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

public interface KeyValueStoreSupplier {

  KeyValueBytesStoreSupplier get(Stores store);
}
