package com.flomaestro.engine.pd;

import static org.apache.kafka.streams.state.Stores.persistentKeyValueStore;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

@ApplicationScoped
public class PersistentKeyValueStoreSupplier implements KeyValueStoreSupplier {

  @Inject TenantNamespaceNameWrapper tenantNamespaceNameWrapper;

  @Override
  public KeyValueBytesStoreSupplier get(Stores store) {
    return persistentKeyValueStore(tenantNamespaceNameWrapper.getPrefixed(store.getStorename()));
  }
}
