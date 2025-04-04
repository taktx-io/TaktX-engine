package com.flomaestro.engine.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TaktConfiguration {
  @ConfigProperty(name = "takt.engine.tenant")
  @Getter
  String tenant;

  @ConfigProperty(name = "takt.engine.namespace")
  @Getter
  String namespace;

  @ConfigProperty(name = "takt.engine.keyvaluestoretype")
  @Getter
  String supplierType;

  @ConfigProperty(name = "takt.engine.topic.replication-factor")
  @Getter
  int replicationFactor;

  @ConfigProperty(name = "takt.engine.topic.partitions")
  @Getter
  int partitions;

  @ConfigProperty(name = "kafka.bootstrap.servers")
  @Getter
  String bootstrapServers;

  @ConfigProperty(name = "quarkus.profile")
  @Getter
  String activeProfiles;

  public String getPrefixed(String name) {
    String prefixedName = tenant + "." + namespace + "." + name;
    if (prefixedName.length() > 100) {
      throw new IllegalArgumentException("Topic name is too long: " + prefixedName);
    }
    return prefixedName;
  }
}
