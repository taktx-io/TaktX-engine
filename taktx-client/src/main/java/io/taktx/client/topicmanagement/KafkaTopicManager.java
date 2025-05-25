package io.taktx.client.topicmanagement;

import io.taktx.dto.TopicMetaDTO;
import io.taktx.util.TaktPropertiesHelper;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;

@Slf4j
public class KafkaTopicManager {
  private final AdminClient adminClient;
  private final TaktPropertiesHelper taktPropertiesHelper;

  public KafkaTopicManager(TaktPropertiesHelper taktPropertiesHelper, Properties kafkaProperties) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.adminClient = AdminClient.create(kafkaProperties);
  }

  public CompletableFuture<TopicMetaDTO> ensureTopicMatches(TopicMetaDTO topicMetaDTO) {
    CompletableFuture<TopicMetaDTO> future = new CompletableFuture<>();

    String topicName = taktPropertiesHelper.getPrefixedTopicName(topicMetaDTO.getTopicName());

    topicDescription(topicName)
        .thenAccept(
            topicDescription -> {
              if (topicDescription != null) {
                log.info("Topic {} already exists, skipping creation.", topicName);
                // Check partitions and replication factor
                if (topicDescription.partitions().size() < topicMetaDTO.getNrPartitions()) {
                  log.warn(
                      "Topic {} has {} partitions, expected {}. Increasing partition count...",
                      topicName,
                      topicDescription.partitions().size(),
                      topicMetaDTO.getNrPartitions());
                  adminClient
                      .createPartitions(
                          Collections.singletonMap(
                              topicName, NewPartitions.increaseTo(topicMetaDTO.getNrPartitions())))
                      .all()
                      .whenComplete(
                          (v, ex) -> {
                            if (ex != null) {
                              log.error(
                                  "Failed to increase partitions for topic {}", topicName, ex);
                              future.completeExceptionally(ex);
                            } else {
                              log.info(
                                  "Successfully increased partitions for topic {} to {}",
                                  topicName,
                                  topicMetaDTO.getNrPartitions());
                              future.complete(
                                  new TopicMetaDTO(topicName, topicMetaDTO.getNrPartitions()));
                            }
                          });
                } else {
                  future.complete(
                      new TopicMetaDTO(topicName, topicDescription.partitions().size()));
                }
              } else {
                log.info("Topic {} does not exist, creating...", topicName);
                NewTopic newTopic =
                    new NewTopic(
                        topicName,
                        topicMetaDTO.getNrPartitions(),
                        taktPropertiesHelper.getDefaultReplicationFactor());
                adminClient
                    .createTopics(Collections.singleton(newTopic))
                    .all()
                    .whenComplete(
                        (v, ex) -> {
                          if (ex != null) {
                            future.completeExceptionally(ex);
                          } else {
                            future.complete(
                                new TopicMetaDTO(topicName, topicMetaDTO.getNrPartitions()));
                          }
                        });
              }
            });
    return future;
  }

  private CompletableFuture<TopicDescription> topicDescription(String topicName) {
    CompletableFuture<TopicDescription> future = new CompletableFuture<>();

    adminClient
        .describeTopics(List.of(topicName))
        .allTopicNames()
        .whenComplete(
            (topicDescription, ex) -> {
              if (ex != null) {
                future.complete(null);
              } else {
                future.complete(topicDescription.values().iterator().next());
              }
            });

    return future;
  }

  public void close() {
    if (adminClient != null) {
      adminClient.close();
    }
  }
}
