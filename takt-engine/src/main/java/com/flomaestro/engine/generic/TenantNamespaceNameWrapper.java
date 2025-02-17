package com.flomaestro.engine.generic;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@NoArgsConstructor
@AllArgsConstructor
public class TenantNamespaceNameWrapper {

  @ConfigProperty(name = "nl.flommaestro.takt.tenant")
  String tenant;

  @ConfigProperty(name = "nl.flommaestro.takt.namespace")
  String namespace;

  public String getPrefixed(String name) {
    String prefixedName = tenant + "." + namespace + "." + name;
    if (prefixedName.length() > 100) {
      throw new IllegalArgumentException("Topic name is too long: " + prefixedName);
    }
    return prefixedName;
  }
}
