package com.flomaestro.engine.generic;

import static org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore;

import com.flomaestro.engine.pd.Stores;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

@ApplicationScoped
@InMemory
public class InMemoryKeyValueStoreSupplier implements KeyValueStoreSupplier {
  @Inject TenantNamespaceNameWrapper tenantNamespaceNameWrapper;

  @Override
  public KeyValueBytesStoreSupplier get(Stores store) {
    return inMemoryKeyValueStore(tenantNamespaceNameWrapper.getPrefixed(store.getStorename()));
  }
}
