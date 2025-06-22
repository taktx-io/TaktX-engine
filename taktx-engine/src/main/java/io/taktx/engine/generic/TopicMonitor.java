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
import io.taktx.engine.license.LicenseManager;
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
  private ReadOnlyKeyValueStore<String, TopicMetaDTO> requestedTopicInfoStore;
  private final KafkaStreams kafkaStreams;
  private final TaktConfiguration taktConfiguration;
  private final AdminClient adminClient;
  private final LicenseManager licenseManager;
  private final Map<String, TopicMetaDTO> kafkaTopicInfo = new ConcurrentHashMap<>();
  private KafkaStreams.State state;

  @PostConstruct
  void init() {
    kafkaStreams.setStateListener(
        (newState, oldState) -> {
          log.info("New state changed from {} to {}", oldState, newState);
          state = newState;
        });
  }

  @Scheduled(every = "10S", delay = 10, delayUnit = TimeUnit.SECONDS)
  public void scanTopicsMeta() {

    scanAndProcessTopics(null);
  }

  public void scanAndProcessTopics(Set<String> topicNamesTooScan) {
    if (requestedTopicInfoStore == null && state == KafkaStreams.State.RUNNING) {
      getRequestedTopicInfoStore();
    }

    if (requestedTopicInfoStore != null) {
      updateKafkaTopicInfo(topicNamesTooScan);
      checkLicenseConditions();
    }
  }

  private void checkLicenseConditions() {
    Map<String, TopicMetaDTO> topicPartitions = getLatestTopicInfo();

    int partitionLimit = licenseManager.getPartitionLimit();

    topicPartitions.forEach(
        (topic, topicMeta) -> {
          if (topicMeta.getNrPartitions() > partitionLimit) {
            String errorMessage =
                String.format(
                    "❌ LICENSE VIOLATION: Maximum allowed partitions is %d, but %d partitions found in topic: %s. ",
                    partitionLimit, topicMeta.getNrPartitions(), topic);
            System.out.println(errorMessage);
            log.error(errorMessage);
            // Exit the application
            String shuttingDownMessage =
                "Shutting down due to license violation. Please contact support at [[https://taktx.io/contact]] to resolve this.";
            log.error(shuttingDownMessage);
            System.out.println(shuttingDownMessage);
            Runtime.getRuntime().halt(1);
          }
        });
  }

  private void updateKafkaTopicInfo(Set<String> topicNamesTooScan) {
    Map<String, TopicMetaDTO> currentTopicMetas = new HashMap<>();
    try (KeyValueIterator<String, TopicMetaDTO> all = requestedTopicInfoStore.all()) {
      all.forEachRemaining(topic -> currentTopicMetas.put(topic.key, topic.value));
    }

    if (topicNamesTooScan == null || topicNamesTooScan.isEmpty()) {
      // If no specific topics to scan, use all topics in the store
      topicNamesTooScan = currentTopicMetas.keySet();
    }

    Map<String, String> topicsToScan =
        topicNamesTooScan.stream()
            .collect(Collectors.toMap(taktConfiguration::getPrefixed, name -> name));
    try {
      // Get a map of futures for each topic

      Map<String, TopicDescription> mapKafkaFuture =
          adminClient.describeTopics(topicsToScan.keySet()).allTopicNames().get();

      // Create a set to track which topics still exist
      Set<String> existingTopicNames = new HashSet<>();

      // Process each future individually
      for (Map.Entry<String, TopicDescription> entry : mapKafkaFuture.entrySet()) {
        String prefixedName = entry.getKey();
        String originalName = topicsToScan.get(prefixedName);

        // Try to get the topic description
        TopicDescription topicDescription = entry.getValue();

        // Topic exists, process it
        existingTopicNames.add(originalName);

        // Create or update the topic metadata
        TopicMetaDTO topicMeta = currentTopicMetas.get(originalName);
        if (topicMeta == null) {
          topicMeta = new TopicMetaDTO();
          topicMeta.setTopicName(originalName);
        }

        topicMeta.setNrPartitions(topicDescription.partitions().size());
        kafkaTopicInfo.put(originalName, topicMeta);
      }

      // Remove entries for topics that no longer exist
      kafkaTopicInfo.keySet().removeIf(name -> !existingTopicNames.contains(name));

    } catch (ExecutionException e) {
      log.error("Failed to get topic information", e);
    } catch (InterruptedException e) {
      log.error("Interrupted while getting topic information", e);
      Thread.currentThread().interrupt();
    }
  }

  private void getRequestedTopicInfoStore() {
    StoreQueryParameters<? extends ReadOnlyKeyValueStore<String, TopicMetaDTO>>
        storeQueryParameters =
            StoreQueryParameters.fromNameAndType(
                taktConfiguration.getPrefixed(Topics.TOPIC_META_TOPIC.getTopicName()),
                QueryableStoreTypes.keyValueStore());
    try {
      requestedTopicInfoStore = kafkaStreams.store(storeQueryParameters);
    } catch (StreamsNotStartedException e) {
      log.error("Failed to get topic meta store", e);
    }
  }

  public Map<String, TopicMetaDTO> getLatestTopicInfo() {
    return kafkaTopicInfo;
  }
}
