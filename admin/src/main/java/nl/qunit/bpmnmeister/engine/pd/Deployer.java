package nl.qunit.bpmnmeister.engine.pd;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import nl.qunit.bpmnmeister.Topics;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

@ApplicationScoped
@Startup
public class Deployer {
  @Inject AdminClient adminClient;

  @PostConstruct
  void init() {
    adminClient.createTopics(
        Arrays.stream(Topics.values())
            .map(topic -> new NewTopic(topic.getTopicName(), 5, (short) 1))
            .toList());
  }
}
