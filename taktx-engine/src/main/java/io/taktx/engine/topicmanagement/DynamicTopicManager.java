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
import io.taktx.Topics;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.KafkaClientsConfig;
import io.taktx.engine.generic.TopologyProducer;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
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
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final AtomicBoolean isLeader = new AtomicBoolean(false);
  private final ConcurrentHashMap<String, TopicMetaDTO> cachedRequestTopicMetaMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, TopicMetaDTO> cachedActualTopicMetaMap = new ConcurrentHashMap<>();
  private KafkaProducer<String, TopicMetaDTO> topicMetaProducer;

  public void start(KafkaProducer<String, TopicMetaDTO> topicMetaProducer) {
    this.topicMetaProducer = topicMetaProducer;
    scanActual();

    if (taktConfiguration.getTopicCreationEnabled()) {
      scanRequest();
    }
  }

  private void scanActual() {
    executor.submit(() -> {
      try (KafkaConsumer<String, TopicMetaDTO> actualConsumer = createConsumer()) {
        String prefixedActualTopicName = taktConfiguration.getPrefixed(Topics.TOPIC_META_ACTUAL_TOPIC.getTopicName());
        actualConsumer.subscribe(List.of(prefixedActualTopicName));

        while (true) {
          var records = actualConsumer.poll(Duration.ofMillis(100));
          records.forEach(topicRecord -> {
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
    executor.submit(() -> {
      try (KafkaConsumer<String, TopicMetaDTO> requestConsumer = createConsumer()) {
        String prefixedActualTopicName = taktConfiguration.getPrefixed(Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName());
        requestConsumer.subscribe(List.of(prefixedActualTopicName),
            getConsumerRebalanceListener(prefixedActualTopicName));

        Map<String, TopicMetaDTO> collectedTopics = new ConcurrentHashMap<>();

        while (true) {
          var records = requestConsumer.poll(Duration.ofMillis(100));
          if (records.isEmpty() && !collectedTopics.isEmpty()) {
            for (Map.Entry<String, TopicMetaDTO> entry : collectedTopics.entrySet()) {
              log.info("Processing topic meta request record {}", entry.getKey());
              var topicMeta = entry.getValue();
              cachedRequestTopicMetaMap.put(topicMeta.getTopicName(), topicMeta);

              if (createTopicIfNotExists(topicMeta.getTopicName(), topicMeta.getNrPartitions(),
                  taktConfiguration.getReplicationFactor())) {
                publishTopicMetaActual(topicMeta.getTopicName(), topicMeta);
              }
              cachedActualTopicMetaMap.put(topicMeta.getTopicName(), topicMeta);
            }
            collectedTopics.clear();
            continue;
          }
          records.forEach(topicRecord -> {
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
        boolean revokedPartitionZero = partitions.stream()
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
        boolean assignedPartitionZero = partitions.stream()
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
      Map<String, KafkaFuture<TopicDescription>> topicDescriptionFutures = describeTopicsResult.topicNameValues();

      // For each cached topic, check if it matches the actual topic
      for (Map.Entry<String, TopicMetaDTO> entry : cachedRequestTopicMetaMap.entrySet()) {
        String prefixedTopicName = entry.getKey();
        TopicMetaDTO cachedTopicMeta = entry.getValue();

        compareAndPublishChanges(topicDescriptionFutures, prefixedTopicName, cachedTopicMeta, prefixedTopicNames);
      }
    } catch (Exception e) {
      log.error("Failed to adapt to external changes", e);
    }
  }

  private void compareAndPublishChanges(Map<String, KafkaFuture<TopicDescription>> topicDescriptionFutures,
      String prefixedTopicName,
      TopicMetaDTO cachedRequestTopicMeta, Set<String> prefixedTopicNames) {
    try {
      // Try to get the actual topic description
      TopicDescription actualTopicDescription = topicDescriptionFutures.get(prefixedTopicName).get();

      // Compare actual vs cached values
      TopicMetaDTO actualTopicMeta = new TopicMetaDTO();
      actualTopicMeta.setTopicName(prefixedTopicName);
      actualTopicMeta.setCleanupPolicy(cachedRequestTopicMeta.getCleanupPolicy());
      actualTopicMeta.setNrPartitions(actualTopicDescription.partitions().size());

      TopicMetaDTO cachedActualTopicMeta = cachedActualTopicMetaMap.get(prefixedTopicName);
      // If we found differences, publish the actual topic info
      if (!cachedRequestTopicMeta.equals(actualTopicMeta) && !actualTopicMeta.equals(cachedActualTopicMeta)) {
        log.info("Found differences for topic {}: cached={}, actual={}",
            prefixedTopicNames, cachedRequestTopicMeta, actualTopicMeta);
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
    try {
      String actualTopicName = taktConfiguration.getPrefixed(Topics.TOPIC_META_ACTUAL_TOPIC.getTopicName());
      ProducerRecord<String, TopicMetaDTO> topicRecord = new ProducerRecord<>(
          actualTopicName,
          topicName,
          topicMeta
      );
      topicMetaProducer.send(topicRecord, (metadata, exception) -> {
        if (exception != null) {
          log.error("Failed to send topic meta actual update for {}", topicName, exception);
        }
      });
    } catch (Exception e) {
      log.error("Error publishing topic meta actual for {}", topicName, e);
    }
  }

  private boolean createTopicIfNotExists(String prefixedTopicName, int numPartitions, short replicationFactor) {
    try {
      // First check if the topic exists
      boolean topicExists = adminClient.listTopics().names().get().contains(prefixedTopicName);

      if (!topicExists) {
        // Create new topic if it doesn't exist
        NewTopic newTopic = new NewTopic(prefixedTopicName, numPartitions, replicationFactor);
        adminClient.createTopics(List.of(newTopic)).all().get();
        log.info("Topic {} created successfully with {} partitions", prefixedTopicName, numPartitions);
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

  private KafkaConsumer<String, TopicMetaDTO> createConsumer() {
    String groupId = "taktx-topicmanager-consumer";

    log.info("Creating consumer for group id {}", groupId);
    Properties props = new Properties();
    props.putAll(kafkaClientsConfig.getConfig());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    return new KafkaConsumer<>(props, TopologyProducer.TOPIC_META_KEY_SERDE.deserializer(),
        TopologyProducer.TOPIC_META_SERDE.deserializer());
  }

  public boolean topicExists(String topicName) {
    String prefixedTopicName = taktConfiguration.getPrefixed(topicName);
    return cachedActualTopicMetaMap.containsKey(prefixedTopicName);
  }
}
