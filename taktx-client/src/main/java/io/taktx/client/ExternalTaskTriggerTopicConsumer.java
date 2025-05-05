package io.taktx.client;

import io.taktx.client.serdes.ExternalTaskTriggerJsonDeserializer;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDDeserializer;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@Slf4j
public class ExternalTaskTriggerTopicConsumer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private KafkaConsumer<UUID, ExternalTaskTriggerDTO> externalTaskTriggerKafkaConsumer;

  private volatile boolean running = false;

  ExternalTaskTriggerTopicConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  public void subscribeToExternalTaskTriggerTopics(ExternalTaskTriggerConsumer externalTaskTriggerConsumer) {
    externalTaskTriggerKafkaConsumer = createConsumer();

    log.info("Subscribing to job ids {}", externalTaskTriggerConsumer.getJobIds());
    subscribe(externalTaskTriggerConsumer.getJobIds());

    running = true;

    CompletableFuture.runAsync(
        () -> {
          while (running) {
            ConsumerRecords<UUID, ExternalTaskTriggerDTO> records =
                externalTaskTriggerKafkaConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<UUID, ExternalTaskTriggerDTO> externalTaskTriggerRecord :
                records) {
              externalTaskTriggerConsumer.accept(externalTaskTriggerRecord.value());
            }
          }

          externalTaskTriggerKafkaConsumer.unsubscribe();
          externalTaskTriggerKafkaConsumer.close();
        },
        executor);
  }

  public void stop() {
    running = false;
  }

  private void subscribe(Set<String> jobIds) {
    List<String> topics = jobIds.stream()
        .map(jobId -> taktPropertiesHelper.getPrefixedTopicName("external-task-trigger-" + jobId)).toList();
    externalTaskTriggerKafkaConsumer.subscribe(topics);
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() {
    String groupId = "taktx-client-external-task-trigger-consumer";

    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId,
            TaktUUIDDeserializer.class,
            ExternalTaskTriggerJsonDeserializer.class,
            "latest");
    return new KafkaConsumer<>(props);
  }
}
