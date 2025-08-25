/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.topicmanagement;

import io.quarkus.runtime.Startup;
import io.taktx.Topics;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.KafkaClientsConfig;
import io.taktx.engine.generic.TopologyProducer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
@Startup
public class TopicBootstrapper {
  private final AdminClient adminClient;
  private final TaktConfiguration taktConfiguration;
  private final KafkaClientsConfig kafkaClientsConfig;
  private final DynamicTopicManager topicManager;
  private KafkaProducer<String, TopicMetaDTO> topicMetaProducer;

  @PostConstruct
  public void init() {
    if (taktConfiguration.getTopicCreationEnabled()) {
      topicMetaProducer =
          new KafkaProducer<>(
              kafkaClientsConfig.getConfig(),
              TopologyProducer.TOPIC_META_KEY_SERDE.serializer(),
              TopologyProducer.TOPIC_META_SERDE.serializer());

      log.info("Topic management enabled, bootstrapping topics");
      if (bootstrapFixedTopics()) {
        bootstrapManagedTopics();
      }
    }
    topicManager.start(topicMetaProducer);
  }

  private boolean bootstrapFixedTopics() {
    List<NewTopic> newTopics =
        Topics.initialFixedTopics().stream()
            .map(
                topic ->
                    new NewTopic(
                        taktConfiguration.getPrefixed(topic.getTopicName()),
                        1,
                        taktConfiguration.getReplicationFactor()))
            .toList();
    try {
      // Make the createTopics call blocking by using get()
      adminClient.createTopics(newTopics).all().get();
      log.info("Bootstrap topics created successfully");
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Topic bootstrap interrupted", e);
    } catch (Exception ex) {
      if (ex.getCause() instanceof TopicExistsException) {
        log.warn("Bootstrap topics already exist");
        return false;
      } else {
        throw new IllegalStateException("Failed to bootstrap topics", ex);
      }
    }
  }

  private void bootstrapManagedTopics() {

    List<NewTopic> newTopics =
        Topics.managedFixedTopics().stream()
            .map(
                topic ->
                    new NewTopic(
                        taktConfiguration.getPrefixed(topic.getTopicName()),
                        taktConfiguration.getPartitions(),
                        taktConfiguration.getReplicationFactor()))
            .toList();

    try {
      adminClient.createTopics(newTopics).all().get();
      log.info("Bootstrap Managed topics created successfully");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Restore the interrupted status
      throw new IllegalStateException("Topic bootstrap interrupted", e);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to bootstrap topics", ex);
    }

    Topics.managedFixedTopics()
        .forEach(
            topic -> {
              String prefixedTopicName = taktConfiguration.getPrefixed(topic.getTopicName());

              TopicMetaDTO topicMetaDTO =
                  new TopicMetaDTO(
                      prefixedTopicName,
                      taktConfiguration.getPartitions(),
                      topic.getCleanupPolicy());

              topicMetaProducer.send(
                  new ProducerRecord<>(
                      taktConfiguration.getPrefixed(Topics.TOPIC_META_ACTUAL_TOPIC.getTopicName()),
                      prefixedTopicName,
                      topicMetaDTO));
              topicMetaProducer.send(
                  new ProducerRecord<>(
                      taktConfiguration.getPrefixed(
                          Topics.TOPIC_META_REQUESTED_TOPIC.getTopicName()),
                      prefixedTopicName,
                      topicMetaDTO));
            });
  }
}
