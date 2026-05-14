/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.testengine;

import static io.taktx.engine.pi.testengine.BpmnTestEngine.DEFAULT_DURATION;
import static io.taktx.engine.pi.testengine.BpmnTestEngine.TOPIC_TEST_PREFIX;

import io.taktx.Topics;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.TopicPartition;
import org.awaitility.Awaitility;
import org.jboss.logging.Logger;

public class AdminClientHelper {

  private static final Logger LOG = Logger.getLogger(AdminClientHelper.class);

  private final AdminClient adminClient;

  public AdminClientHelper(String bootstrapServers) {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(AdminClientConfig.CLIENT_ID_CONFIG, "consumer-lag-monitor");

    this.adminClient = AdminClient.create(props);
  }

  public void waitForKafkaBroker(String bootstrapServers) {
    LOG.info("Waiting for Kafka broker to start on: " + bootstrapServers);

    Awaitility.await()
        .atMost(DEFAULT_DURATION)
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              try {
                // This will throw an exception if broker is not available
                Set<String> actualTopics = adminClient.listTopics().names().get();
                LOG.info("Kafka broker is now available");
                Set<String> requiredTopics =
                    Arrays.stream(Topics.values())
                        .map(topic -> TOPIC_TEST_PREFIX + topic.getTopicName())
                        .collect(Collectors.toSet());
                boolean allTopicsAvailable = actualTopics.containsAll(requiredTopics);
                if (!allTopicsAvailable) {
                  LOG.info("Not all topics available: " + actualTopics);
                } else {
                  LOG.info("All required topics are available: " + actualTopics);
                }
                return allTopicsAvailable;
              } catch (Exception e) {
                LOG.info("Waiting for Kafka broker to start: " + e.getMessage());
                return false;
              }
            });
  }

  /** Get consumer lag for a specific consumer group and topic */
  public ConsumerLagInfo getConsumerLag(String consumerGroupId, String topicName) {
    try {
      // Get consumer group description
      Map<String, ConsumerGroupDescription> consumerGroups =
          adminClient.describeConsumerGroups(Collections.singleton(consumerGroupId)).all().get();

      ConsumerGroupDescription groupDescription = consumerGroups.get(consumerGroupId);
      if (groupDescription == null) {
        LOG.warn("Consumer group not found: " + consumerGroupId);
        return new ConsumerLagInfo(consumerGroupId, topicName, -1, ConsumerGroupState.UNKNOWN);
      }

      // Get consumer group offsets
      Map<TopicPartition, OffsetAndMetadata> consumerOffsets =
          adminClient
              .listConsumerGroupOffsets(consumerGroupId)
              .partitionsToOffsetAndMetadata()
              .get();

      // Filter offsets for the specific topic
      Map<TopicPartition, OffsetAndMetadata> topicOffsets = new HashMap<>();
      for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : consumerOffsets.entrySet()) {
        if (entry.getKey().topic().equals(topicName)) {
          topicOffsets.put(entry.getKey(), entry.getValue());
        }
      }

      if (topicOffsets.isEmpty()) {
        LOG.warn(
            "No offsets found for topic: " + topicName + " in consumer group: " + consumerGroupId);
        return new ConsumerLagInfo(consumerGroupId, topicName, 0, groupDescription.state());
      }

      // Get latest offsets for topic partitions
      Map<TopicPartition, OffsetSpec> latestOffsetSpecs = new HashMap<>();
      for (TopicPartition tp : topicOffsets.keySet()) {
        latestOffsetSpecs.put(tp, OffsetSpec.latest());
      }

      ListOffsetsResult latestOffsetsResult = adminClient.listOffsets(latestOffsetSpecs);
      Map<TopicPartition, Long> latestOffsets = new HashMap<>();
      for (Map.Entry<TopicPartition, OffsetSpec> entry : latestOffsetSpecs.entrySet()) {
        TopicPartition tp = entry.getKey();
        long latestOffset = latestOffsetsResult.partitionResult(tp).get().offset();
        latestOffsets.put(tp, latestOffset);
      }

      // Calculate total lag
      long totalLag = 0;
      Map<Integer, Long> partitionLags = new HashMap<>();

      for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : topicOffsets.entrySet()) {
        TopicPartition tp = entry.getKey();
        long consumerOffset = entry.getValue().offset();
        long latestOffset = latestOffsets.get(tp);
        long lag = latestOffset - consumerOffset;

        totalLag += lag;
        partitionLags.put(tp.partition(), lag);
      }

      return new ConsumerLagInfo(
          consumerGroupId, topicName, totalLag, groupDescription.state(), partitionLags);

    } catch (InterruptedException | ExecutionException e) {
      LOG.error(
          "Error getting consumer lag for group: " + consumerGroupId + ", topic: " + topicName, e);
      return new ConsumerLagInfo(consumerGroupId, topicName, -1, ConsumerGroupState.UNKNOWN);
    }
  }

  /** Get consumer lag for all topics in a consumer group */
  public Map<String, ConsumerLagInfo> getAllConsumerLags(String consumerGroupId) {
    Map<String, ConsumerLagInfo> result = new HashMap<>();

    try {
      // Get consumer group offsets
      Map<TopicPartition, OffsetAndMetadata> consumerOffsets =
          adminClient
              .listConsumerGroupOffsets(consumerGroupId)
              .partitionsToOffsetAndMetadata()
              .get();

      // Group by topic
      Map<String, Map<TopicPartition, OffsetAndMetadata>> topicOffsets = new HashMap<>();
      for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : consumerOffsets.entrySet()) {
        String topic = entry.getKey().topic();
        topicOffsets
            .computeIfAbsent(topic, k -> new HashMap<>())
            .put(entry.getKey(), entry.getValue());
      }

      // Calculate lag for each topic
      for (String topic : topicOffsets.keySet()) {
        ConsumerLagInfo lagInfo = getConsumerLag(consumerGroupId, topic);
        result.put(topic, lagInfo);
      }

    } catch (InterruptedException | ExecutionException e) {
      LOG.error("Error getting all consumer lags for group: " + consumerGroupId, e);
    }

    return result;
  }

  /** List all consumer groups */
  public Set<String> listConsumerGroups() {
    try {
      Collection<ConsumerGroupListing> consumerGroupListings =
          adminClient.listConsumerGroups().all().get();

      return consumerGroupListings.stream()
          .map(ConsumerGroupListing::groupId)
          .collect(Collectors.toSet());
    } catch (InterruptedException | ExecutionException e) {
      LOG.error("Error listing consumer groups", e);
      return Collections.emptySet();
    }
  }

  public void close() {
    if (adminClient != null) {
      adminClient.close();
    }
  }

  /** Data class to hold consumer lag information */
  public static class ConsumerLagInfo {

    private final String consumerGroupId;
    private final String topicName;
    private final long totalLag;
    private final ConsumerGroupState state;
    private final Map<Integer, Long> partitionLags;

    public ConsumerLagInfo(
        String consumerGroupId, String topicName, long totalLag, ConsumerGroupState state) {
      this(consumerGroupId, topicName, totalLag, state, new HashMap<>());
    }

    public ConsumerLagInfo(
        String consumerGroupId,
        String topicName,
        long totalLag,
        ConsumerGroupState state,
        Map<Integer, Long> partitionLags) {
      this.consumerGroupId = consumerGroupId;
      this.topicName = topicName;
      this.totalLag = totalLag;
      this.state = state;
      this.partitionLags = partitionLags;
    }

    public String getConsumerGroupId() {
      return consumerGroupId;
    }

    public String getTopicName() {
      return topicName;
    }

    public long getTotalLag() {
      return totalLag;
    }

    public ConsumerGroupState getState() {
      return state;
    }

    public Map<Integer, Long> getPartitionLags() {
      return partitionLags;
    }

    @Override
    public String toString() {
      return String.format(
          "ConsumerLagInfo{group='%s', topic='%s', totalLag=%d, state=%s, partitionLags=%s}",
          consumerGroupId, topicName, totalLag, state, partitionLags);
    }
  }
}
