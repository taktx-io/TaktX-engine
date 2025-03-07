package com.flomaestro.app;

import com.flomaestro.client.TaktClient;
import com.flomaestro.client.TaktClient.TaktClientBuilder;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.io.IOException;

@ApplicationScoped
@Startup
public class TaktClientProvider {
  private TaktClient taktClient;

  @PostConstruct
  void init() throws IOException {
    TaktClientBuilder taktClientBuilder = TaktClient.newClientBuilder();
    taktClient = taktClientBuilder.withTenant("tenant").withNamespace("namespace").build();
  }

  @Produces
  TaktClient taktClient() {
    return taktClient;
  }
}
