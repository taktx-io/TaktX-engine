package io.taktx.engine.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Getter
public class TaktConfiguration {
  @ConfigProperty(name = "taktx.engine.tenant")
  String tenant;

  @ConfigProperty(name = "taktx.engine.namespace")
  String namespace;

  @ConfigProperty(name = "taktx.engine.keyvaluestoretype")
  String supplierType;

  @ConfigProperty(name = "taktx.engine.topic.replication-factor")
  int replicationFactor;

  @ConfigProperty(name = "taktx.engine.topic.partitions")
  int partitions;

  @ConfigProperty(name = "kafka.bootstrap.servers")
  String bootstrapServers;

  @ConfigProperty(name = "taktx.test", defaultValue = "false")
  String isTest;

  @ConfigProperty(name = "taktx.engine.host", defaultValue = "false")
  String host;

  @ConfigProperty(name = "taktx.engine.port", defaultValue = "8080")
  int port;

  public boolean inTestMode() {
    return Boolean.parseBoolean(isTest);
  }

  public String getPrefixed(String name) {
    String prefixedName = tenant + "." + namespace + "." + name;
    if (prefixedName.length() > 254) {
      throw new IllegalArgumentException("Topic name is too long: " + prefixedName);
    }
    return prefixedName;
  }
}
