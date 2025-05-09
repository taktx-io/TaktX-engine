package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.ProcessDefinitionJsonDeserializer;
import io.taktx.client.serdes.ProcessDefinitionKeyJsonDeserializer;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.util.TaktPropertiesHelper;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@Slf4j
public class ProcessDefinitionConsumer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private final Map<ProcessDefinitionKey, String> storedHashes = new ConcurrentHashMap<>();
  private final Map<ProcessDefinitionKey, ProcessDefinitionDTO> definitionMap =
      new ConcurrentHashMap<>();
  private final Map<UUID, Consumer<ConsumerRecord<ProcessDefinitionKey, ProcessDefinitionDTO>>>
      processDefinitionUpdateConsumers = new ConcurrentHashMap<>();
  private KafkaConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> definitionActivationConsumer;

  private volatile boolean running = false;

  ProcessDefinitionConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  public void subscribeToDefinitionRecords() {
    definitionActivationConsumer = createConsumer();

    String prefixedTopicName =
        taktPropertiesHelper.getPrefixedTopicName(
            Topics.PROCESS_DEFINITION_ACTIVATION_TOPIC.getTopicName());

    log.info("Subscribing to topic {}", prefixedTopicName);
    subscribe(prefixedTopicName);

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
                  (key, consumer) -> consumer.accept(activationRecord));
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

  public Map<ProcessDefinitionKey, ProcessDefinitionDTO> getDeployedProcessDefinitions() {
    return definitionMap;
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
      Consumer<ConsumerRecord<ProcessDefinitionKey, ProcessDefinitionDTO>> consumer) {
    UUID consumerKey = UUID.randomUUID();
    processDefinitionUpdateConsumers.put(consumerKey, consumer);
    return consumerKey;
  }

  private void subscribe(String prefixedTopicName) {
    definitionActivationConsumer.subscribe(Collections.singletonList(prefixedTopicName));
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() {
    String groupId = "client-definition-activation-consumer-" + UUID.randomUUID();

    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId,
            ProcessDefinitionKeyJsonDeserializer.class,
            ProcessDefinitionJsonDeserializer.class,
            "earliest");
    return new KafkaConsumer<>(props);
  }
}
