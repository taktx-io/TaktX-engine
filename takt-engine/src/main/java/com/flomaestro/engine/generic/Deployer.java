package com.flomaestro.engine.generic;

import com.flomaestro.takt.Topics;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Startup
public class Deployer {
  @Inject AdminClient adminClient;

  @ConfigProperty(name = "takt.engine.topic.partitions")
  int partitions;

  @ConfigProperty(name = "takt.engine.tenant")
  String tenant;

  @ConfigProperty(name = "takt.engine.namespace")
  String namespace;

  @ConfigProperty(name = "takt.engine.topic.replication-factor")
  int replicationFactor;

  @PostConstruct
  void init() {
    try {
      ListTopicsResult listTopicsResult = adminClient.listTopics();

      List<String> toCreate = new ArrayList<>();
      List<String> list = listTopicsResult.names().get().stream().toList();
      for (Topics topic : Topics.values()) {
        String name = tenant + "." + namespace + "." + topic.getTopicName();
        if (list.contains(name)) {
          continue;
        }
        toCreate.add(name);
      }

      CreateTopicsResult topics =
          adminClient.createTopics(
              toCreate.stream()
                  .map(
                      topic -> {
                        return new NewTopic(topic, partitions, (short) replicationFactor);
                      })
                  .toList());
      topics.all().get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
