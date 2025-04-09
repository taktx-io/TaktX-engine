package io.taktx.client;

import io.taktx.client.serdes.ExternalTaskTriggerJsonDeserializer;
import io.taktx.dto.v_1_0_0.ExternalTaskTriggerDTO;
import io.taktx.dto.v_1_0_0.ProcessDefinitionDTO;
import io.taktx.dto.v_1_0_0.ProcessDefinitionKey;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDDeserializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * This class is responsible for managing the subscription to external tasks for a single process
 * definition.
 */
@Slf4j
public class ExternalTasksForProcessDefinitionConsumer
    implements Consumer<ConsumerRecord<ProcessDefinitionKey, ProcessDefinitionDTO>> {

  private final Executor executor;
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Map<String, List<Consumer<ConsumerRecord<UUID, ExternalTaskTriggerDTO>>>>
      externalTaskTriggerConsumers = new HashMap<>();
  private final Map<String, AtomicBoolean> runningConsumers = new HashMap<>();

  private final long externalTriggerPollMs;

  public ExternalTasksForProcessDefinitionConsumer(
      TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.executor = executor;
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.externalTriggerPollMs =
        Long.parseLong(
            taktPropertiesHelper
                .getCommonProperties()
                .getOrDefault("poll.timeout", 100)
                .toString());
  }

  public void stop() {
    runningConsumers.values().forEach(r -> r.set(false));
  }

  public void registerExternalTaskTriggerConsumer(
      String processDefinitionId, Consumer<ConsumerRecord<UUID, ExternalTaskTriggerDTO>> consumer) {
    log.info("Registering external task consumer for process definition {}", processDefinitionId);
    List<Consumer<ConsumerRecord<UUID, ExternalTaskTriggerDTO>>> consumers =
        externalTaskTriggerConsumers.computeIfAbsent(processDefinitionId, k -> new ArrayList<>());
    consumers.add(consumer);
    subscribeIfNotSubscribedAndConsumersAvailable(processDefinitionId);
  }

  @Override
  public void accept(
      ConsumerRecord<ProcessDefinitionKey, ProcessDefinitionDTO> processDefinitionRecord) {
    ProcessDefinitionKey processDefinitionKey = processDefinitionRecord.key();
    subscribeIfNotSubscribedAndConsumersAvailable(processDefinitionKey.getProcessDefinitionId());
  }

  private void subscribeIfNotSubscribedAndConsumersAvailable(String processDefinitionId) {
    if (!externalTaskTriggerConsumers.isEmpty()
        && runningConsumers.get(processDefinitionId) == null) {
      runningConsumers.put(processDefinitionId, new AtomicBoolean(true));
      subscribeToTopic(processDefinitionId);
    }
  }

  private void subscribeToTopic(final String processDefinitionId) {
    CompletableFuture.runAsync(
        () -> {
          log.info(
              "Subscribing to external task triggers for process definition {}",
              processDefinitionId);
          try (KafkaConsumer<UUID, ExternalTaskTriggerDTO> consumer =
              createConsumer("external-task-consumer-" + processDefinitionId)) {

            consumer.subscribe(
                Collections.singletonList(
                    taktPropertiesHelper.getPrefixedTopicName(
                        "external-task-trigger-" + processDefinitionId)));

            while (runningConsumers.get(processDefinitionId).get()) {
              ConsumerRecords<UUID, ExternalTaskTriggerDTO> records =
                  consumer.poll(Duration.ofMillis(externalTriggerPollMs));

              for (var externalTriggerRecord : records) {
                log.info(
                    "Consuming external task trigger for process definition {} to {} consumers",
                    processDefinitionId,
                    externalTaskTriggerConsumers.get(processDefinitionId).size());
                externalTaskTriggerConsumers
                    .getOrDefault(processDefinitionId, new ArrayList<>())
                    .forEach(c -> c.accept(externalTriggerRecord));
              }
            }
          }
        },
        executor);
  }

  private <K, V> KafkaConsumer<K, V> createConsumer(String groupId) {
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId, TaktUUIDDeserializer.class, ExternalTaskTriggerJsonDeserializer.class);
    return new KafkaConsumer<>(props);
  }
}
