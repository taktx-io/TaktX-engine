/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.topicmanagement;

import io.taktx.CleanupPolicy;
import io.taktx.Topics;
import io.taktx.dto.Constants;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.serdes.ExternalTaskMetaSerializer;
import io.taktx.util.TaktPropertiesHelper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class ExternalTaskTopicRequester {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final KafkaProducer<String, TopicMetaDTO> producer;

  public ExternalTaskTopicRequester(TaktPropertiesHelper taktPropertiesHelper) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.producer =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(),
            new StringSerializer(),
            new ExternalTaskMetaSerializer());
  }

  /**
   * Requests a worker topic with the default settings: 3 partitions, DELETE cleanup policy,
   * replication factor 1. Use the full overload to tune the partition count against the
   * deployment's partition budget.
   */
  public String requestExternalTaskTopic(String externalTaskId) {
    return requestExternalTaskTopic(externalTaskId, 3, CleanupPolicy.DELETE, (short) 1);
  }

  public String requestExternalTaskTopic(
      String externalTaskId, int partitions, CleanupPolicy cleanupPolicy, short replicationFactor) {
    String topicName =
        taktPropertiesHelper.getPrefixedTopicName(
            Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + externalTaskId);
    TopicMetaDTO topicMetaDTO =
        new TopicMetaDTO(topicName, partitions, cleanupPolicy, replicationFactor);

    producer.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(
                Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName()),
            topicName,
            topicMetaDTO));
    return topicName;
  }
}
