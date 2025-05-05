package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.ExternalTaskMetaJsonDeserializer;
import io.taktx.dto.ExternalTaskMetaDTO;
import io.taktx.util.TaktPropertiesHelper;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

@Slf4j
public class ExternalTaskMetaTopicConsumer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private KafkaConsumer<String, ExternalTaskMetaDTO> externalTaskMetaConsumer;

  private volatile boolean running = false;

  ExternalTaskMetaTopicConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  public void subscribeToExternalTaskMetaInfo(ExternalTaskMetaConsumer metaConsumer) {
    externalTaskMetaConsumer = createConsumer();

    subscribe();

    running = true;

    CompletableFuture.runAsync(
        () -> {
          while (running) {
            ConsumerRecords<String, ExternalTaskMetaDTO> records =
                externalTaskMetaConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, ExternalTaskMetaDTO> metaRecord : records) {
              metaConsumer.accept(metaRecord.key(), metaRecord.value());
            }
          }

          externalTaskMetaConsumer.unsubscribe();
          externalTaskMetaConsumer.close();
        },
        executor);
  }

  public void stop() {
    running = false;
  }

  private void subscribe() {
    String prefixedTopicName = taktPropertiesHelper.getPrefixedTopicName(
        Topics.EXTERNAL_TASK_META_TOPIC.getTopicName());
    externalTaskMetaConsumer.subscribe(Collections.singletonList(prefixedTopicName));
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() {
    String groupId = "taktx-client-external-task-meta-consumer-" + UUID.randomUUID();

    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId,
            StringDeserializer.class,
            ExternalTaskMetaJsonDeserializer.class,
            Topics.EXTERNAL_TASK_META_TOPIC.getAutoOffsetReset());
    return new KafkaConsumer<>(props);
  }
}
