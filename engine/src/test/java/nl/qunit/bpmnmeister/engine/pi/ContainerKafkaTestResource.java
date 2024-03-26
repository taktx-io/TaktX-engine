package nl.qunit.bpmnmeister.engine.pi;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class ContainerKafkaTestResource implements QuarkusTestResourceLifecycleManager {

  public static final KafkaContainer kafka = new KafkaContainer("7.2.6");

  /**
   * @return A map of system properties that should be set for the running tests
   */
  @Override
  public Map<String, String> start() {
    kafka.start();
    System.out.println("Kafka bootstrap servers: " + kafka.getBootstrapServers());
    Map<String, String> systemProperties = new HashMap<>();
    systemProperties.put("kafka.bootstrap.servers", kafka.getBootstrapServers());
    systemProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    systemProperties.put("group.min.session.timeout.ms", "100"); // Set session timeout to 60 seconds
    systemProperties.put("group.max.session.timeout.ms", "60000"); // Set session timeout to 60 seconds
    return systemProperties;
  }

  @Override
  public void stop() {
    kafka.close();
  }
}