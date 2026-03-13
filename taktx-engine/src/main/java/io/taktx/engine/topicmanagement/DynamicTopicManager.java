/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.topicmanagement;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.taktx.CleanupPolicy;
import io.taktx.Topics;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.KafkaClientsConfig;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.license.LicenseManager;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
@Startup
public class DynamicTopicManager {

  private final AdminClient adminClient;
  private final TaktConfiguration taktConfiguration;
  private final KafkaClientsConfig kafkaClientsConfig;
  private final LicenseManager licenseManager;
  private final ExecutorService executor =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "taktx-topic-manager");
            t.setDaemon(true);
            return t;
          });
  private final AtomicBoolean isLeader = new AtomicBoolean(false);
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final ConcurrentHashMap<String, TopicMetaDTO> cachedRequestTopicMetaMap =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, TopicMetaDTO> cachedActualTopicMetaMap =
      new ConcurrentHashMap<>();
  private KafkaProducer<String, TopicMetaDTO> topicMetaProducer;
  // Cached once at startup — avoids CDI proxy access from background threads
  private String cachedActualTopicName;
  private String cachedRequestedTopicName;

  public void start(KafkaProducer<String, TopicMetaDTO> topicMetaProducer) {
    this.topicMetaProducer = topicMetaProducer;
    this.cachedActualTopicName =
        taktConfiguration.getPrefixed(Topics.TOPIC_META_ACTUAL_TOPIC.getTopicName());
    this.cachedRequestedTopicName =
        taktConfiguration.getPrefixed(Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName());
    scanActual();

    if (taktConfiguration.getTopicCreationEnabled()) {
      scanRequest();
    }
  }

  @PreDestroy
  public void stop() {
    log.info("Shutting down DynamicTopicManager");
    running.set(false);
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
    }
  }

  private void scanActual() {
    executor.submit(
        () -> {
          try (KafkaConsumer<String, TopicMetaDTO> actualConsumer =
              createConsumer("taktx-topicmanager-actuel-consumer-" + UUID.randomUUID())) {
            actualConsumer.subscribe(List.of(cachedActualTopicName));

            while (running.get()) {
              var records = actualConsumer.poll(Duration.ofMillis(100));
              records.forEach(
                  topicRecord -> {
                    log.info(
                        "Processing topic meta actual record {} {}",
                        topicRecord.key(),
                        topicRecord.value());
                    if (topicRecord.value() == null) {
                      cachedActualTopicMetaMap.remove(topicRecord.key());
                    } else {
                      cachedActualTopicMetaMap.put(topicRecord.key(), topicRecord.value());
                    }
                  });
            }
          }
        });
  }

  private void scanRequest() {
    executor.submit(
        () -> {
          try (KafkaConsumer<String, TopicMetaDTO> requestConsumer =
              createConsumer("taktx-topicmanager-request-consumer")) {
            requestConsumer.subscribe(
                List.of(cachedRequestedTopicName),
                getConsumerRebalanceListener(cachedRequestedTopicName));

            Map<String, TopicMetaDTO> collectedTopics = new ConcurrentHashMap<>();

            while (running.get()) {
              var records = requestConsumer.poll(Duration.ofMillis(100));
              if (records.isEmpty() && !collectedTopics.isEmpty()) {
                for (Map.Entry<String, TopicMetaDTO> entry : collectedTopics.entrySet()) {
                  log.info("Processing topic meta request record {}", entry.getKey());
                  var topicMeta = entry.getValue();
                  cachedRequestTopicMetaMap.put(topicMeta.getTopicName(), topicMeta);
                  int budget = licenseManager.getPartitionBudget();
                  int currentTotal = computeCurrentTotal();
                  int requested = topicMeta.getNrPartitions();
                  if (budget == 0 || currentTotal + requested <= budget) {
                    if (createTopicIfNotExists(
                        topicMeta.getTopicName(),
                        topicMeta.getNrPartitions(),
                        topicMeta.getCleanupPolicy(),
                        topicMeta.getReplicationFactor())) {
                      publishTopicMetaActual(topicMeta.getTopicName(), topicMeta);
                    }
                    cachedActualTopicMetaMap.put(topicMeta.getTopicName(), topicMeta);
                  } else {
                    log.warn(
                        "Partition budget exceeded — topic '{}' requesting {} partition(s) rejected. "
                            + "Current total: {}, budget: {}. Upgrade license or reduce partition counts.",
                        topicMeta.getTopicName(),
                        requested,
                        currentTotal,
                        budget);
                    // Publish a tombstone so any watcher knows the request was rejected.
                    publishTopicMetaActual(topicMeta.getTopicName(), null);
                  }
                }
                collectedTopics.clear();
                continue;
              }
              records.forEach(
                  topicRecord -> {
                    if (topicRecord.value() == null) {
                      collectedTopics.remove(topicRecord.key());
                    } else {
                      collectedTopics.put(topicRecord.key(), topicRecord.value());
                    }
                  });
            }
          }
        });
  }

  private ConsumerRebalanceListener getConsumerRebalanceListener(String prefixedActualTopicName) {
    return new ConsumerRebalanceListener() {
      @Override
      public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        boolean revokedPartitionZero =
            partitions.stream()
                .anyMatch(tp -> tp.topic().equals(prefixedActualTopicName) && tp.partition() == 0);

        if (revokedPartitionZero) {
          boolean wasLeader = isLeader.getAndSet(false);
          if (wasLeader) {
            log.info("No longer the leader for topic management");
          }
        }
      }

      @Override
      public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        boolean assignedPartitionZero =
            partitions.stream()
                .anyMatch(tp -> tp.topic().equals(prefixedActualTopicName) && tp.partition() == 0);

        if (assignedPartitionZero) {
          boolean wasLeader = isLeader.getAndSet(true);
          if (!wasLeader) {
            log.info("Became the leader for topic management");
          }
        }
      }
    };
  }

  @Scheduled(every = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  public void adaptToExternalChanges() {
    if (!isLeader.get()) {
      log.debug("Not the leader, skipping adaptToExternalChanges");
      return;
    }

    if (cachedRequestTopicMetaMap.isEmpty()) {
      log.info("No cached topic metadata to check");
      return;
    }

    try {
      // Get all the topic names from our cache
      Set<String> prefixedTopicNames = cachedRequestTopicMetaMap.keySet();

      // Get descriptions for all topics we know about
      DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(prefixedTopicNames);
      Map<String, KafkaFuture<TopicDescription>> topicDescriptionFutures =
          describeTopicsResult.topicNameValues();

      // For each cached topic, check if it matches the actual topic
      for (Map.Entry<String, TopicMetaDTO> entry : cachedRequestTopicMetaMap.entrySet()) {
        String prefixedTopicName = entry.getKey();
        TopicMetaDTO cachedTopicMeta = entry.getValue();

        compareAndPublishChanges(
            topicDescriptionFutures, prefixedTopicName, cachedTopicMeta, prefixedTopicNames);
      }
    } catch (Exception e) {
      log.error("Failed to adapt to external changes", e);
    }
  }

  private void compareAndPublishChanges(
      Map<String, KafkaFuture<TopicDescription>> topicDescriptionFutures,
      String prefixedTopicName,
      TopicMetaDTO cachedRequestTopicMeta,
      Set<String> prefixedTopicNames) {
    try {
      // Try to get the actual topic description
      TopicDescription actualTopicDescription =
          topicDescriptionFutures.get(prefixedTopicName).get();
      short actualReplicationFactor =
          (short) actualTopicDescription.partitions().get(0).replicas().size();

      // Compare actual vs cached values
      TopicMetaDTO actualTopicMeta =
          new TopicMetaDTO(
              prefixedTopicName,
              actualTopicDescription.partitions().size(),
              cachedRequestTopicMeta.getCleanupPolicy(),
              actualReplicationFactor);
      actualTopicMeta.setTopicName(prefixedTopicName);
      actualTopicMeta.setCleanupPolicy(cachedRequestTopicMeta.getCleanupPolicy());
      actualTopicMeta.setNrPartitions(actualTopicDescription.partitions().size());

      // Check the total partition budget
      int budget = licenseManager.getPartitionBudget();
      int currentTotal = computeCurrentTotal();
      if (budget == 0 || currentTotal > budget) {
        log.warn(
            "Total partition count ({}) exceeds license budget ({}). "
                + "New topic requests will be rejected until the total is within budget.",
            currentTotal,
            budget);
        // Do not halt — existing topics continue to function.
      }

      // Fixed managed topics must never be repartitioned at runtime.
      if (!isWorkerTopic(prefixedTopicName)) {
        if (actualTopicMeta.getNrPartitions() != cachedRequestTopicMeta.getNrPartitions()) {
          log.warn(
              "Fixed managed topic '{}' has {} partition(s) on the broker but {} configured. "
                  + "Repartitioning fixed topics requires a full state store rebuild. "
                  + "To change: drain the engine, delete the topic and all changelog topics, "
                  + "update taktx.engine.partitions, then restart.",
              prefixedTopicName,
              actualTopicMeta.getNrPartitions(),
              cachedRequestTopicMeta.getNrPartitions());
        }
        // Fall through to the normal diff-publish logic for non-partition differences.
      } else {
        // Worker topic — safe to increase partition count.
        int actualPartitions = actualTopicMeta.getNrPartitions();
        int requestedPartitions = cachedRequestTopicMeta.getNrPartitions();
        if (actualPartitions < requestedPartitions) {
          int remaining =
              budget == Integer.MAX_VALUE
                  ? Integer.MAX_VALUE
                  : budget - currentTotal + actualPartitions;
          int newCount = Math.min(requestedPartitions, actualPartitions + remaining);
          if (newCount > actualPartitions) {
            try {
              adminClient
                  .createPartitions(Map.of(prefixedTopicName, NewPartitions.increaseTo(newCount)))
                  .all()
                  .get();
              log.info(
                  "Increased partition count for worker topic '{}': {} → {}",
                  prefixedTopicName,
                  actualPartitions,
                  newCount);
              actualTopicMeta.setNrPartitions(newCount);
            } catch (Exception e) {
              log.warn(
                  "Failed to increase partitions for '{}': {}", prefixedTopicName, e.getMessage());
            }
          } else {
            log.warn(
                "Cannot increase partitions for worker topic '{}' from {} to {} — budget exhausted.",
                prefixedTopicName,
                actualPartitions,
                requestedPartitions);
          }
        }
      }

      TopicMetaDTO cachedActualTopicMeta = cachedActualTopicMetaMap.get(prefixedTopicName);
      // If we found differences, publish the actual topic info
      if (!cachedRequestTopicMeta.equals(actualTopicMeta)
          && !actualTopicMeta.equals(cachedActualTopicMeta)) {
        log.info(
            "Found differences for topic {}: cached={}, actual={}",
            prefixedTopicNames,
            cachedRequestTopicMeta,
            actualTopicMeta);

        publishTopicMetaActual(prefixedTopicName, actualTopicMeta);
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Restore the interrupted status
      throw new IllegalStateException("Topic bootstrap interrupted", e);
    } catch (Exception ex) {
      if (ex.getCause() instanceof UnknownTopicOrPartitionException) {
        // Topic doesn't exist anymore
        if (cachedActualTopicMetaMap.containsKey(prefixedTopicName)) {
          log.info("Topic {} no longer exists, sending null update", prefixedTopicName);
          cachedActualTopicMetaMap.remove(prefixedTopicName);
          publishTopicMetaActual(prefixedTopicName, null);
        }
      } else {
        log.error("Error checking topic {}: {}", prefixedTopicName, ex.getMessage(), ex);
      }
    }
  }

  private void publishTopicMetaActual(String topicName, TopicMetaDTO topicMeta) {
    if (!running.get()) {
      return;
    }
    try {
      log.info("Publishing topic meta to ACTUAL: " + topicMeta);
      ProducerRecord<String, TopicMetaDTO> topicRecord =
          new ProducerRecord<>(cachedActualTopicName, topicName, topicMeta);
      topicMetaProducer.send(
          topicRecord,
          (metadata, exception) -> {
            if (exception != null) {
              log.error("Failed to send topic meta actual update for {}", topicName, exception);
            }
          });
    } catch (Exception e) {
      log.error("Error publishing topic meta actual for {}", topicName, e);
    }
  }

  private boolean createTopicIfNotExists(
      String prefixedTopicName,
      int numPartitions,
      CleanupPolicy cleanupPolicy,
      short replicationFactor) {
    try {
      // First check if the topic exists
      boolean topicExists = adminClient.listTopics().names().get().contains(prefixedTopicName);

      if (!topicExists) {
        // Create new topic if it doesn't exist
        NewTopic newTopic = new NewTopic(prefixedTopicName, numPartitions, replicationFactor);
        // Apply cleanup policy configuration
        newTopic.configs(java.util.Map.of("cleanup.policy", cleanupPolicy.getKafkaPolicyValue()));

        adminClient.createTopics(List.of(newTopic)).all().get();
        log.info(
            "Topic {} created successfully with {} partitions", prefixedTopicName, numPartitions);
        return true;
      }
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Restore the interrupted status
      throw new IllegalStateException("Topic bootstrap interrupted", e);
    } catch (Exception e) {
      log.error("Failed to create or update topic {}", prefixedTopicName, e);
      return false;
    }
  }

  private KafkaConsumer<String, TopicMetaDTO> createConsumer(String groupId) {
    log.info("Creating consumer for group id {}", groupId);
    Properties props = new Properties();
    props.putAll(kafkaClientsConfig.getConfig());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    return new KafkaConsumer<>(
        props,
        TopologyProducer.TOPIC_META_KEY_SERDE.deserializer(),
        TopologyProducer.TOPIC_META_SERDE.deserializer());
  }

  /**
   * The 4 initial fixed topics ({@code topic-meta-requested}, {@code topic-meta-actual}, {@code
   * taktx-configuration}, {@code taktx-signing-keys}) are always created with exactly 1 partition
   * each and are never published to {@code topic-meta-actual}, so they never appear in {@code
   * cachedActualTopicMetaMap}. This constant accounts for their fixed partition cost.
   */
  static final int INITIAL_FIXED_TOPIC_PARTITION_COST = 4;

  /**
   * Returns the current total number of managed partitions: the sum of all partitions in {@code
   * cachedActualTopicMetaMap} plus the constant cost of the initial fixed topics.
   */
  private int computeCurrentTotal() {
    int mapTotal =
        cachedActualTopicMetaMap.values().stream().mapToInt(TopicMetaDTO::getNrPartitions).sum();
    return mapTotal + INITIAL_FIXED_TOPIC_PARTITION_COST;
  }

  /**
   * Returns {@code true} if the given topic name matches the worker topic prefix ({@code
   * external-task-trigger-*}), meaning it is safe to increase its partition count at runtime. Fixed
   * managed topics must never be repartitioned.
   */
  private boolean isWorkerTopic(String topicName) {
    return topicName.contains(io.taktx.dto.Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX);
  }

  public boolean topicExists(String topicName) {
    String prefixedTopicName = taktConfiguration.getPrefixed(topicName);
    return cachedActualTopicMetaMap.containsKey(prefixedTopicName);
  }
}
