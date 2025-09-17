/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.util;

import java.util.Properties;
import lombok.Getter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;

@Getter
public class TaktPropertiesHelper {

  private final String tenant;

  private final String namespace;

  private final Properties taktProperties;

  public TaktPropertiesHelper(String tenant, String namespace, Properties taktProperties) {
    this.tenant = tenant;
    this.namespace = namespace;
    this.taktProperties = taktProperties;
  }

  public Properties getKafkaConsumerProperties(
      String groupId,
      Class<?> keyDeserializer,
      Class<?> valueDeserializer,
      String autoOffsetResetConfig) {
    Properties props = new Properties();

    // Sensible defaults for high-rate tiny messages
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000); // commit after processing
    props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1_048_576); // 1 MB
    props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100); // wait to coalesce fetch
    props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 16 * 1024 * 1024); // 16 MB
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 2_000);
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10_000);
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 1_000);
    props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 900_000); // 15 min for slow handlers
    props.put(
        ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
        CooperativeStickyAssignor.class.getName()); // stable rebalances

    // Let external config override any of the above
    if (taktProperties != null && !taktProperties.isEmpty()) {
      props.putAll(taktProperties);
    }

    // Required fields (set last so they cannot be overridden)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());
    props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig);
    return props;
  }

  public Properties getKafkaProducerProperties(
      Class<? extends Serializer<?>> keySerializer,
      Class<? extends Serializer<?>> valueSerializer) {
    Properties props = new Properties();

    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    props.put(
        ProducerConfig.COMPRESSION_TYPE_CONFIG,
        "none"); // No compression for already compact messages
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 262_144); // 256 KB
    props.put(ProducerConfig.LINGER_MS_CONFIG, 10); // 5–15 ms
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 536_870_912L); // 512 MB
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);

    // Let external config override any of the above
    if (taktProperties != null && !taktProperties.isEmpty()) {
      props.putAll(taktProperties);
    }

    // If a transactional.id is provided externally, ensure idempotence is on
    if (props.containsKey(ProducerConfig.TRANSACTIONAL_ID_CONFIG)) {
      props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    }

    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer.getName());
    return props;
  }

  public String getPrefixedTopicName(String topic) {
    return tenant + "." + namespace + "." + topic;
  }

  public boolean getAutoCreate() {
    return Boolean.parseBoolean(taktProperties.getProperty("taktx.auto.create.topics", "true"));
  }

  public int getDefaultPartitions() {
    return 3;
  }

  public short getDefaultReplicationFactor() {
    return 1;
  }

  public int getExternalTaskConsumerThreads() {
    return Integer.parseInt(
        taktProperties.getOrDefault("taktx.external.task.consumer.threads", 1).toString());
  }

  public int getExternalTaskConsumerMaxPollRecords() {
    return Integer.parseInt(
        taktProperties
            .getOrDefault("taktx.external.task.consumer.max.poll.records", 500)
            .toString());
  }

  public int getExternalTaskConsumerPollTimeoutMs() {
    return Integer.parseInt(
        taktProperties
            .getOrDefault("taktx.external.task.consumer.poll.timeout.ms", 100)
            .toString());
  }
}
