package nl.qunit.bpmnmeister.app;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import nl.qunit.bpmnmeister.client.TaktClient;
import nl.qunit.bpmnmeister.client.TaktClient.TaktClientBuilder;

@ApplicationScoped
@Startup
public class TaktClientProvider {
  private TaktClient taktClient;

  @PostConstruct
  void init() {
    TaktClientBuilder taktClientBuilder = TaktClient.newClientBuilder();
    taktClient = taktClientBuilder
        .withBootstrapServers("localhost:9092")
        .withTenant("tenant")
        .withNamespace("namespace")
        .build();
  }

  @Produces
  TaktClient taktClient() {
    return taktClient;
  }
}
