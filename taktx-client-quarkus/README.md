This module provides Quarkus convenience beans for the TaktX plain Java client.

It exposes a CDI producer `io.taktx.client.quarkus.QuarkusTaktClientProducer` that creates a
`io.taktx.client.TaktClient` using MicroProfile Config properties:

- `taktx.namespace` - the namespace used for topics
- `kafka.bootstrap.servers` - bootstrap servers for Kafka client

The module is configured to be published to Maven Central similar to `taktx-client` and `taktx-shared`.
package io.taktx.client.quarkus;

import io.taktx.client.TaktClient;
import io.taktx.util.TaktPropertiesHelper;
import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A minimal CDI producer that creates a TaktClient.Builder using MicroProfile config.
 * This allows Quarkus applications to inject a ready-to-use TaktClient.
 */
@ApplicationScoped
public class QuarkusTaktClientProducer {

  @ConfigProperty(name = "taktx.namespace")
  String namespace;

  @ConfigProperty(name = "kafka.bootstrap.servers")
  String kafkaBootstrap;

  @Produces
  public TaktClient produceTaktClient() {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafkaBootstrap);

    TaktClient.TaktClientBuilder builder = TaktClient.newClientBuilder().withNamespace(namespace).withKafkaProperties(props);
    return builder.build();
  }
}

