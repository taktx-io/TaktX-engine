package nl.qunit.bpmnmeister.engine.pd;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import nl.qunit.bpmnmeister.Topics;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Startup
public class Deployer {
  @Inject AdminClient adminClient;

  @ConfigProperty(name = "kafka.topic.replication-factor")
  int replicationFactor;

  @PostConstruct
  void init() {
    try {
      CreateTopicsResult topics =
          adminClient.createTopics(
              Arrays.stream(Topics.values())
                  .map(topic -> new NewTopic(topic.getTopicName(), 5, (short) replicationFactor))
                  .toList());
      topics.all().get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
