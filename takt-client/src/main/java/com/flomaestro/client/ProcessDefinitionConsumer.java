package com.flomaestro.client;

import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.util.TaktPropertiesHelper;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

@Slf4j
public class ProcessDefinitionConsumer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private final Map<ProcessDefinitionKey, String> storedHashes = new ConcurrentHashMap<>();
  private final Map<ProcessDefinitionKey, ProcessDefinitionDTO> definitionMap =
      new ConcurrentHashMap<>();
  private final Map<UUID, BiConsumer<ProcessDefinitionKey, ProcessDefinitionDTO>>
      processDefinitionUpdateConsumers = new ConcurrentHashMap<>();
  private KafkaConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> definitionActivationConsumer;

  private volatile boolean running = false;

  ProcessDefinitionConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  public void subscribeToDefinitionRecords() throws IOException {
    definitionActivationConsumer = createConsumer();

    String prefixedTopicName =
        taktPropertiesHelper.getPrefixedTopicName(
            Topics.PROCESS_DEFINITION_ACTIVATION_TOPIC.getTopicName());

    log.info("Subscribing to topic {}", prefixedTopicName);
    subscribeAndWaitUntilPartitionsAssigned(prefixedTopicName);

    running = true;

    CompletableFuture.runAsync(
        () -> {
          while (running) {
            ConsumerRecords<ProcessDefinitionKey, ProcessDefinitionDTO> records =
                definitionActivationConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<ProcessDefinitionKey, ProcessDefinitionDTO> activationRecord :
                records) {
              log.info(
                  "Received definition activation record {} to state {}",
                  activationRecord.key(),
                  activationRecord.value().getState());
              final String storedHash = storedHashes.get(activationRecord.key());
              if (storedHash != null
                  && !storedHash.equals(
                      activationRecord.value().getDefinitions().getDefinitionsKey().getHash())) {
                log.warn(
                    "Hash mismatch for process definition {} {}",
                    activationRecord.key(),
                    activationRecord.value());
              }

              storedHashes.put(
                  activationRecord.key(),
                  activationRecord.value().getDefinitions().getDefinitionsKey().getHash());

              definitionMap.put(activationRecord.key(), activationRecord.value());

              log.info(
                  "Notifying {} consumers of process definition update",
                  processDefinitionUpdateConsumers.size());
              processDefinitionUpdateConsumers.forEach(
                  (key, consumer) ->
                      consumer.accept(activationRecord.key(), activationRecord.value()));
            }
          }

          definitionActivationConsumer.unsubscribe();
          definitionActivationConsumer.close();
        },
        executor);
  }

  public void stop() {
    running = false;
  }

  public Map<ProcessDefinitionKey, ProcessDefinitionDTO> getDeployedProcessDefinitions(
      String processDefinitionId) {
    return definitionMap.entrySet().stream()
        .filter(e -> e.getKey().getProcessDefinitionId().equals(processDefinitionId))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  public Optional<ProcessDefinitionDTO> getDeployedProcessDefinitionbyHash(
      String processDefinitionId, String hash) {
    return definitionMap.entrySet().stream()
        .filter(
            e ->
                e.getKey().getProcessDefinitionId().equals(processDefinitionId)
                    && e.getValue().getDefinitions().getDefinitionsKey().getHash().equals(hash))
        .map(Entry::getValue)
        .findFirst();
  }

  public UUID subscribeToProcessDefinitionUpdates(
      BiConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> consumer) {
    UUID consumerKey = UUID.randomUUID();
    processDefinitionUpdateConsumers.put(consumerKey, consumer);
    return consumerKey;
  }

  private void subscribeAndWaitUntilPartitionsAssigned(String prefixedTopicName) {
    AtomicBoolean assigned = new AtomicBoolean(false);

    definitionActivationConsumer.subscribe(
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
      ConsumerRecords<ProcessDefinitionKey, ProcessDefinitionDTO> poll =
          definitionActivationConsumer.poll(Duration.ofMillis(100));
      if (poll.count() > 0) {
        log.error(
            "Topic {} Received {} records before expecting to received",
            prefixedTopicName,
            poll.count());
      }
    }
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() throws IOException {
    String groupId = "client-definition-activation-consumer-" + UUID.randomUUID();

    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId,
            ProcessDefinitionKeyJsonDeserializer.class,
            ProcessDefinitionJsonDeserializer.class);
    return new KafkaConsumer<>(props);
  }
}
