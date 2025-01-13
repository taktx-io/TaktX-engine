package com.flomaestro.takt.analyze;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
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
