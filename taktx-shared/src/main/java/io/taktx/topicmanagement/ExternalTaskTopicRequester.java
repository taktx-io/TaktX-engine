/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
            taktPropertiesHelper.getKafkaProducerProperties(
                StringSerializer.class, ExternalTaskMetaSerializer.class));
  }

  public String requestExternalTaskTopic(String externalTaskId, int partitions, CleanupPolicy cleanupPolicy) {
    TopicMetaDTO topicMetaDTO = new TopicMetaDTO();
    String topicName = taktPropertiesHelper.getPrefixedTopicName(Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + externalTaskId);
    topicMetaDTO.setTopicName(topicName);
    topicMetaDTO.setNrPartitions(partitions);
    topicMetaDTO.setCleanupPolicy(cleanupPolicy);
    producer.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName()),
            topicName,
            topicMetaDTO));
    return topicName;
  }
}
