This module provides Quarkus convenience beans for the TaktX plain Java client.

It exposes a CDI producer `io.taktx.client.quarkus.QuarkusTaktXClientProducer` that creates a
`io.taktx.client.TaktXClient` using MicroProfile Config properties:

- `taktx.namespace` - the namespace used for topics
- `kafka.bootstrap.servers` - bootstrap servers for Kafka client

The module is configured to be published to Maven Central similar to `taktx-client` and `taktx-shared`.
package io.taktx.client.quarkus;

import io.taktx.client.TaktXClient;
import io.taktx.util.TaktPropertiesHelper;
import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A minimal CDI producer that creates a TaktXClient.Builder using MicroProfile config.
 * This allows Quarkus applications to inject a ready-to-use TaktXClient.
 */
@ApplicationScoped
public class QuarkusTaktXClientProducer {

  @ConfigProperty(name = "taktx.namespace")
  String namespace;

  @ConfigProperty(name = "kafka.bootstrap.servers")
  String kafkaBootstrap;

  @Produces
  public TaktXClient produceTaktXClient() {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafkaBootstrap);

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder().withNamespace(namespace).withKafkaProperties(props);
    return builder.build();
  }
}

