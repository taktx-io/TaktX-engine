package io.taktx.client;

import io.taktx.client.serdes.ExternalTaskTriggerJsonDeserializer;
import io.taktx.dto.Constants;
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
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@Slf4j
public class ExternalTaskTriggerTopicConsumer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private KafkaConsumer<UUID, ExternalTaskTriggerDTO> externalTaskTriggerKafkaConsumer;
  private final Object consumerLock = new Object(); // Lock for synchronization
  private CompletableFuture<Void> consumerFuture;
  private volatile boolean running = false;

  ExternalTaskTriggerTopicConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  public void subscribeToExternalTaskTriggerTopics(
      Set<String> jobIds, Consumer<ExternalTaskTriggerDTO> externalTaskTriggerConsumer) {
    log.info("Subscribing to job ids {}", jobIds);
    List<String> topics =
        jobIds.stream()
            .map(
                jobId ->
                    taktPropertiesHelper.getPrefixedTopicName(
                        Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + jobId))
            .toList();

    // Stop the previous consumer if it's running
    stop();

    // Wait for previous future to complete to avoid thread issues
    if (consumerFuture != null) {
      try {
        consumerFuture.join();
      } catch (Exception e) {
        log.warn("Error while waiting for previous consumer to complete", e);
      }
    }

    synchronized (consumerLock) {
      // Create a new consumer if needed
      if (externalTaskTriggerKafkaConsumer == null) {
        externalTaskTriggerKafkaConsumer = createConsumer();
      }

      // Subscribe to the topics
      externalTaskTriggerKafkaConsumer.subscribe(topics);

      // Mark as running
      running = true;

      // Start the consumer in a new thread
      consumerFuture =
          CompletableFuture.runAsync(
              () -> {
                try {
                  while (running) {
                    synchronized (consumerLock) {
                      if (!running || externalTaskTriggerKafkaConsumer == null) {
                        break;
                      }

                      ConsumerRecords<UUID, ExternalTaskTriggerDTO> records =
                          externalTaskTriggerKafkaConsumer.poll(Duration.ofMillis(100));

                      for (ConsumerRecord<UUID, ExternalTaskTriggerDTO> externalTaskTriggerRecord :
                          records) {
                        externalTaskTriggerConsumer.accept(externalTaskTriggerRecord.value());
                      }
                    }

                    // Small sleep outside the lock to prevent tight loop
                    try {
                      Thread.sleep(10);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      break;
                    }
                  }
                } finally {
                  // Clean up the resources when the thread exits
                  synchronized (consumerLock) {
                    if (externalTaskTriggerKafkaConsumer != null) {
                      try {
                        externalTaskTriggerKafkaConsumer.unsubscribe();
                        externalTaskTriggerKafkaConsumer.close();
                      } catch (Exception e) {
                        log.error("Error closing Kafka consumer", e);
                      } finally {
                        externalTaskTriggerKafkaConsumer = null;
                      }
                    }
                  }
                }
              },
              executor);
    }
  }

  public void stop() {
    running = false;
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
