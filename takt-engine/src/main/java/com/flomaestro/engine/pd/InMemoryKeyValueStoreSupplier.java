package com.flomaestro.engine.pd;

import static org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

@Alternative
@ApplicationScoped
public class InMemoryKeyValueStoreSupplier implements KeyValueStoreSupplier {
  @Inject TenantNamespaceNameWrapper tenantNamespaceNameWrapper;

  @Override
  public KeyValueBytesStoreSupplier get(Stores store) {
    return inMemoryKeyValueStore(tenantNamespaceNameWrapper.getPrefixed(store.getStorename()));
  }
}
