package com.flomaestro.client;

import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.util.TaktPropertiesHelper;
import com.flomaestro.takt.util.TaktUUIDDeserializer;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

/**
 * This class is responsible for managing the subscription to external tasks for all process
 * definitions.
 */
@Slf4j
public class ProcessInstanceUpdateConsumer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private boolean running = false;
  private final List<BiConsumer<UUID, InstanceUpdateDTO>> instanceUpdateConsumers =
      new ArrayList<>();

  public ProcessInstanceUpdateConsumer(
      TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  public void addInstanceUpdateConsumer(BiConsumer<UUID, InstanceUpdateDTO> consumer) {
    if (instanceUpdateConsumers.isEmpty()) {
      subscribeToTopic();
    }
    instanceUpdateConsumers.add(consumer);
  }

  private void subscribeToTopic() {
    running = true;

    CompletableFuture.runAsync(
        () -> {
          try (KafkaConsumer<UUID, InstanceUpdateDTO> consumer = createConsumer()) {

            AtomicBoolean assigned = new AtomicBoolean(false);

            String prefixedTopicName =
                taktPropertiesHelper.getPrefixedTopicName(
                    Topics.INSTANCE_UPDATE_TOPIC.getTopicName());

            consumer.subscribe(
                Collections.singletonList(prefixedTopicName),
                new ConsumerRebalanceListener() {
                  @Override
                  public void onPartitionsRevoked(Collection<TopicPartition> collection) {}

                  @Override
                  public void onPartitionsAssigned(Collection<TopicPartition> collection) {
                    log.info("Partitions assigned: {}", collection);
                    assigned.set(true);
                  }
                });

            while (!assigned.get()) {
              ConsumerRecords<UUID, InstanceUpdateDTO> poll = consumer.poll(Duration.ofMillis(100));
              if (poll.count() > 0) {
                log.error(
                    "Topic {} Received {} records before expecting to received",
                    prefixedTopicName,
                    poll.count());
              }
            }

            while (running) {
              consumeRecords(consumer);
            }

            consumer.unsubscribe();
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        },
        executor);
  }

  private void consumeRecords(KafkaConsumer<UUID, InstanceUpdateDTO> consumer) {
    consumer
        .poll(java.time.Duration.ofMillis(100))
        .forEach(
            record -> {
              instanceUpdateConsumers.forEach(
                  instanceUpdateConsumer ->
                      instanceUpdateConsumer.accept(record.key(), record.value()));
            });
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() throws IOException {
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            "instance-update-consumer",
            TaktUUIDDeserializer.class,
            InstanceUpdateJsonDeserializer.class);
    return new KafkaConsumer<>(props);
  }
}
