package nl.qunit.bpmnmeister.engine.pi.testengine;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.testcontainers.containers.KafkaContainer;

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
    systemProperties.put("auto.create.topics.enable", "true");
    systemProperties.put("num.partitions", "3");
    systemProperties.put("group.min.session.timeout.ms", "100");
    systemProperties.put("group.max.session.timeout.ms", "10000");
    return systemProperties;
  }

  @Override
  public void stop() {
    kafka.close();
  }
}