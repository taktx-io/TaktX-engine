package io.taktx.app;

import io.quarkus.runtime.Startup;
import io.taktx.client.TaktClient;
import io.taktx.client.TaktClient.TaktClientBuilder;
import io.taktx.dto.InstanceUpdateDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BiConsumer;
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
        Properties properties = new Properties();

        String taktPropertiesFile = System.getenv("TAKTX_PROPERTIES_FILE");
        log.info("TaktClientProvider taktPropertiesFile: {}", taktPropertiesFile);
        try (FileInputStream fileInputStream = new FileInputStream(taktPropertiesFile)) {
          properties.load(fileInputStream);
        }

        taktClient =
            taktClientBuilder
                .withTenant(properties.getProperty("taktx.engine.tenant"))
                .withNamespace(properties.getProperty("taktx.engine.namespace"))
                .withKafkaProperties(properties)
                .build();
        taktClient.start();
        taktClient.deployTaktDeploymentAnnotatedClasses();
        taktClient.registerInstanceUpdateConsumer(
            (uuid, instanceUpdateDTO) ->
                log.info("InstanceUpdateDTO: {}", instanceUpdateDTO));
      }
    }
  }

  @Produces
  TaktClient taktClient() {
    return taktClient;
  }
}
