package io.taktx.client.topicmanagement;

import io.taktx.CleanupPolicy;
import io.taktx.Topics;
import io.taktx.client.serdes.ExternalTaskMetaSerializer;
import io.taktx.client.serdes.TopicMetaJsonDeserializer;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.util.TaktPropertiesHelper;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

@Slf4j
public class TopicMatcher {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private final KafkaProducer<String, TopicMetaDTO> producer;
  private KafkaConsumer<String, TopicMetaDTO> topicMetaConsumer;
  private final KafkaTopicManager kafkaTopicManager;
  private final Map<String, TopicMetaDTO> metaMap = new ConcurrentHashMap<>();

  private volatile boolean running = false;

  public TopicMatcher(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
    this.kafkaTopicManager =
        new KafkaTopicManager(taktPropertiesHelper, taktPropertiesHelper.getTaktProperties());

    this.producer =
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(
                StringSerializer.class, ExternalTaskMetaSerializer.class));
  }

  public void start() {
    topicMetaConsumer = createConsumer();

    subscribe();

    running = true;

    CompletableFuture.runAsync(
        () -> {
          while (running) {
            ConsumerRecords<String, TopicMetaDTO> records =
                topicMetaConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, TopicMetaDTO> metaRecord : records) {
              kafkaTopicManager
                  .ensureTopicMatches(metaRecord.value())
                  .whenComplete(
                      (v, e) -> {
                        log.info("Topic {} updated", metaRecord.key());
                        metaMap.put(metaRecord.key(), metaRecord.value());
                      });
            }
          }

          topicMetaConsumer.unsubscribe();
          topicMetaConsumer.close();
        },
        executor);
  }

  public void stop() {
    running = false;
  }

  public void requestTopicState(String topicName, int partitions, CleanupPolicy cleanupPolicy) {
    TopicMetaDTO topicMetaDTO = new TopicMetaDTO();
    topicMetaDTO.setTopicName(topicName);
    topicMetaDTO.setNrPartitions(partitions);
    topicMetaDTO.setCleanupPolicy(cleanupPolicy);
    producer.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(Topics.TOPIC_META_TOPIC.getTopicName()),
            topicName,
            topicMetaDTO));
  }

  public void registerInitialFixedTopics() {
    for (Topics topic : Topics.initialFixedTopics()) {
      TopicMetaDTO topicMeta =
          new TopicMetaDTO(
              topic.getTopicName(),
              taktPropertiesHelper.getDefaultPartitions(),
              topic.getCleanupPolicy());
      kafkaTopicManager
          .ensureTopicMatches(topicMeta)
          .thenAccept(v -> log.info("Fixed topic {} registered and created", topic.getTopicName()))
          .exceptionally(
              ex -> {
                log.error(
                    "Failed to register topic for topic meta info {}: {}",
                    topic.getTopicName(),
                    ex.getMessage());
                return null;
              });
    }
  }

  private void subscribe() {
    String prefixedTopicName =
        taktPropertiesHelper.getPrefixedTopicName(Topics.TOPIC_META_TOPIC.getTopicName());
    topicMetaConsumer.subscribe(Collections.singletonList(prefixedTopicName));
  }

  private KafkaConsumer<String, TopicMetaDTO> createConsumer() {
    String groupId = "taktx-client-topic-meta-consumer";

    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId, StringDeserializer.class, TopicMetaJsonDeserializer.class, "earliest");
    return new KafkaConsumer<>(props);
  }
}
