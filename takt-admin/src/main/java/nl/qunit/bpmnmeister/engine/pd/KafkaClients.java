package nl.qunit.bpmnmeister.engine.pd;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;

@ApplicationScoped
public class KafkaClients {

  @Inject
  @Identifier("default-kafka-broker")
  Map<String, Object> config;

  @Produces
  AdminClient getAdmin() {
    Map<String, Object> copy = new HashMap<>();
    for (Map.Entry<String, Object> entry : config.entrySet()) {
      if (AdminClientConfig.configNames().contains(entry.getKey())) {
        copy.put(entry.getKey(), entry.getValue());
      }
    }
    return KafkaAdminClient.create(copy);
  }
}
