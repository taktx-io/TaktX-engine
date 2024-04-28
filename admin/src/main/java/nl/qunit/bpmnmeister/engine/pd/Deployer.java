package nl.qunit.bpmnmeister.engine.pd;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.ExecutionException;
import nl.qunit.bpmnmeister.Topics;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;

@ApplicationScoped
@Startup
public class Deployer {
  @Inject AdminClient adminClient;

  @PostConstruct
  void init() {
    try {
      CreateTopicsResult topics = adminClient.createTopics(
          Arrays.stream(Topics.values())
              .map(topic -> new NewTopic(topic.getTopicName(), 5, (short) 3))
              .toList());
      topics.all().get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }

  }
}
