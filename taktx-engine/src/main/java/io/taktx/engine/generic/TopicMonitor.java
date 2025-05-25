/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.generic;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.taktx.Topics;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.config.TaktConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.StreamsNotStartedException;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
@Startup
public class TopicMonitor {
  private ReadOnlyKeyValueStore<String, TopicMetaDTO> topicMetaStore;
  private final KafkaStreams kafkaStreams;
  private final TaktConfiguration taktConfiguration;
  private final AdminClient adminClient;
  private final Map<String, TopicMetaDTO> topicInfo = new ConcurrentHashMap<>();
  private KafkaStreams.State state;

  @PostConstruct
  void init() {
    kafkaStreams.setStateListener(
        (newState, oldState) -> {
          log.info("New state changed from {} to {}", oldState, newState);
          state = newState;
        });
  }

  public void addTopicMeta(TopicMetaDTO topicMeta) {
    topicInfo.put(topicMeta.getTopicName(), topicMeta);
  }

  @Scheduled(every = "5S", delay = 5, delayUnit = TimeUnit.SECONDS)
  public void scanTopicsMeta() {
    if (topicMetaStore == null && state == KafkaStreams.State.RUNNING) {
      getTopicMetaStore();
    }

    if (topicMetaStore != null) {
      Map<String, TopicMetaDTO> currentTopicMetas = new HashMap<>();
      try (KeyValueIterator<String, TopicMetaDTO> all = topicMetaStore.all()) {
        all.forEachRemaining(topic -> currentTopicMetas.put(topic.key, topic.value));
      }

      Map<String, String> topicsToScan =
          currentTopicMetas.keySet().stream()
              .collect(Collectors.toMap(taktConfiguration::getPrefixed, name -> name));
      try {
        // Get a map of futures for each topic
        Map<String, KafkaFuture<TopicDescription>> topicFutures =
            adminClient.describeTopics(topicsToScan.keySet()).topicNameValues();

        // Create a set to track which topics still exist
        Set<String> existingTopicNames = new HashSet<>();

        // Process each future individually
        for (Map.Entry<String, KafkaFuture<TopicDescription>> entry : topicFutures.entrySet()) {
          String prefixedName = entry.getKey();
          String originalName = topicsToScan.get(prefixedName);

          // Try to get the topic description
          TopicDescription topicDescription = entry.getValue().get();

          // Topic exists, process it
          existingTopicNames.add(originalName);

          // Create or update the topic metadata
          TopicMetaDTO topicMeta = currentTopicMetas.get(originalName);
          if (topicMeta == null) {
            topicMeta = new TopicMetaDTO();
            topicMeta.setTopicName(originalName);
          }

          topicMeta.setNrPartitions(topicDescription.partitions().size());
          topicInfo.put(originalName, topicMeta);
        }

        // Remove entries for topics that no longer exist
        topicInfo.keySet().removeIf(name -> !existingTopicNames.contains(name));

      } catch (ExecutionException | InterruptedException e) {
        log.error("Failed to get topic information", e);
        Thread.currentThread().interrupt(); // Restore interrupted status
      }
    }
  }

  private void getTopicMetaStore() {
    StoreQueryParameters<? extends ReadOnlyKeyValueStore<String, TopicMetaDTO>>
        storeQueryParameters =
            StoreQueryParameters.fromNameAndType(
                taktConfiguration.getPrefixed(Topics.TOPIC_META_TOPIC.getTopicName()),
                QueryableStoreTypes.keyValueStore());
    try {
      topicMetaStore = kafkaStreams.store(storeQueryParameters);
    } catch (StreamsNotStartedException e) {
      log.error("Failed to get topic meta store", e);
    }
  }

  public Map<String, TopicMetaDTO> getLatestTopicInfo() {
    return topicInfo;
  }
}
