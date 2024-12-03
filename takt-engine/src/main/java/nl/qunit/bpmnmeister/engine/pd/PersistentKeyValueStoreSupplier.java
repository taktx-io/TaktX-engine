package nl.qunit.bpmnmeister.engine.pd;

import static org.apache.kafka.streams.state.Stores.persistentKeyValueStore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.generic.TenantNamespaceNameWrapper;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

@ApplicationScoped
public class PersistentKeyValueStoreSupplier implements KeyValueStoreSupplier {

  @Inject TenantNamespaceNameWrapper tenantNamespaceNameWrapper;

  @Override
  public KeyValueBytesStoreSupplier get(Stores store) {
    return persistentKeyValueStore(tenantNamespaceNameWrapper.getPrefixed(store.getStorename()));
  }
}
