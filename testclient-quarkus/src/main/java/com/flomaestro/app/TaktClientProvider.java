package com.flomaestro.app;

import com.flomaestro.client.TaktClient;
import com.flomaestro.client.TaktClient.TaktClientBuilder;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Startup
@Slf4j
@Priority(Integer.MAX_VALUE) // Lower value means higher priority
public class TaktClientProvider {
  private static TaktClient taktClient;

  public TaktClientProvider() {
    log.info("TaktClientProvider instantiated");
  }

  @PostConstruct
  void init() throws IOException {
    TaktClientBuilder taktClientBuilder = TaktClient.newClientBuilder();
    synchronized (TaktClientProvider.class) {
      if (taktClient == null) {
        taktClient = taktClientBuilder.withTenant("tenant").withNamespace("namespace").build();
        taktClient.start();
        taktClient.scanAndDeployBpmnDefinitions();
      }
    }
  }

  @Produces
  TaktClient taktClient() {
    return taktClient;
  }
}
