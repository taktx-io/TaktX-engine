package io.taktx.app;

import io.quarkus.runtime.Startup;
import io.taktx.client.TaktClient;
import io.taktx.client.TaktClient.TaktClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Startup
@Slf4j
@Priority(Integer.MAX_VALUE) // Lower value means higher priority
public class TaktClientProvider {
  private static TaktClient taktClient;

  @ConfigProperty(name ="takt.engine.tenant", defaultValue = "tenant")
  String tenant;
  @ConfigProperty(name ="takt.engine.namespace", defaultValue = "namespace")
  String namespace;
  @ConfigProperty(name ="takt.engine.namespace", defaultValue = "namespace")
  String namespace;

  public TaktClientProvider() {
    log.info("TaktClientProvider instantiated");
  }

  @PostConstruct
  void init() {
    TaktClientBuilder taktClientBuilder = TaktClient.newClientBuilder();
    synchronized (TaktClientProvider.class) {
      if (taktClient == null) {
        Properties properties = new Properties();
        properties.load(new FileInputStream());
        taktClient = taktClientBuilder
            .withTenant("tenant")
            .withNamespace("namespace")
            .withKafkaProperties(properties)
            .build();
        taktClient.start();
        taktClient.deployTaktDeploymentAnnotatedClasses();
        taktClient.registerAnnotatedWorkers();
      }
    }
  }

  @Produces
  TaktClient taktClient() {
    return taktClient;
  }
}
