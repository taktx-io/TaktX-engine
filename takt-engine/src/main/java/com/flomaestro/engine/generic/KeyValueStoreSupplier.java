package com.flomaestro.engine.generic;

import com.flomaestro.engine.pd.Stores;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

public interface KeyValueStoreSupplier {

  KeyValueBytesStoreSupplier get(Stores store);
}
