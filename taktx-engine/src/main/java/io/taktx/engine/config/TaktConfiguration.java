package io.taktx.engine.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Getter
public class TaktConfiguration {
  @ConfigProperty(name = "takt.engine.tenant")
  String tenant;

  @ConfigProperty(name = "takt.engine.namespace")
  String namespace;

  @ConfigProperty(name = "takt.engine.keyvaluestoretype")
  String supplierType;

  @ConfigProperty(name = "takt.engine.topic.replication-factor")
  int replicationFactor;

  @ConfigProperty(name = "takt.engine.topic.partitions")
  int partitions;

  @ConfigProperty(name = "kafka.bootstrap.servers")
  String bootstrapServers;

  @ConfigProperty(name = "taktx.test", defaultValue = "false")
  String isTest;

  public boolean inTestMode() {
    return Boolean.parseBoolean(isTest);
  }

  public String getPrefixed(String name) {
    String prefixedName = tenant + "." + namespace + "." + name;
    if (prefixedName.length() > 100) {
      throw new IllegalArgumentException("Topic name is too long: " + prefixedName);
    }
    return prefixedName;
  }
}
