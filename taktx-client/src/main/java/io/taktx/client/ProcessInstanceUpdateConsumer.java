package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.InstanceUpdateJsonDeserializer;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;

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

            String prefixedTopicName =
                taktPropertiesHelper.getPrefixedTopicName(
                    Topics.INSTANCE_UPDATE_TOPIC.getTopicName());

            consumer.subscribe(Collections.singletonList(prefixedTopicName));

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
            instanceUpdateRecord ->
                instanceUpdateConsumers.forEach(
                    instanceUpdateConsumer ->
                        instanceUpdateConsumer.accept(
                            instanceUpdateRecord.key(), instanceUpdateRecord.value())));
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() throws IOException {
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            "instance-update-consumer",
            TaktUUIDDeserializer.class,
            InstanceUpdateJsonDeserializer.class,
            Topics.INSTANCE_UPDATE_TOPIC.getAutoOffsetReset());
    return new KafkaConsumer<>(props);
  }
}
