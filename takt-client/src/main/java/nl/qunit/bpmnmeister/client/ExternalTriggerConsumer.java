package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.Constants;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ParsedDefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.XmlDefinitionsDTO;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ExternalTaskResponseResultDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ExternalTaskResponseTriggerDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ExternalTaskResponseType;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ExternalTaskTriggerDTO;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;

@Slf4j
public class ExternalTriggerConsumer {

  private final Map<String, KafkaConsumer<UUID, ExternalTaskTriggerDTO>> consumerMap =
      new HashMap<>();

  private final ObjectMapper objectMapper;
  private final KafkaPropertiesHelper kafkaPropertiesHelper;

  private final Map<String, String> storedHashes = new HashMap<>();
  private final Map<String, Object> definitionMap = new HashMap<>();
  private KafkaConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> definitionActivationConsumer;
  private KafkaProducer<UUID, ExternalTaskResponseTriggerDTO> responseEmitter;
  private final boolean subscriberInitialized = false;

  ExternalTriggerConsumer(KafkaPropertiesHelper kafkaPropertiesHelper) {
    this.objectMapper = new ObjectMapper(new CBORFactory());
    this.kafkaPropertiesHelper = kafkaPropertiesHelper;
  }

  void init() {
    subscribeToDefinitionRecords();
    scanAndDeployBpmnDefinitions();
  }

  public Set<String> getProcessDefinitionConsumers() {
    return consumerMap.keySet();
  }

  private void subscribeToDefinitionRecords() {
    definitionActivationConsumer =
        createConsumer(
            "client-definition-activation-consumer-" + UUID.randomUUID(),
            ProcessDefinitionKeyJsonDeserializer.class,
            ProcessDefinitionJsonDeserializer.class);

    String prefixedTopicName =
        kafkaPropertiesHelper.getPrefixedTopicName(Topics.PROCESS_DEFINITION_ACTIVATION_TOPIC);
    log.info("Subscribing to topic {}", prefixedTopicName);
    AtomicBoolean assigned = new AtomicBoolean(false);

    subscribeAndWaitUntilPartitionsAssigned(prefixedTopicName, assigned);

    responseEmitter =
        new KafkaProducer<>(
            kafkaPropertiesHelper.getKafkaProducerProperties(
                (Class<? extends Serializer<?>>) Serdes.UUID().serializer().getClass(),
                ExternalTaskTriggerResponseSerializer.class));

    CompletableFuture.runAsync(
        () -> {
          while (true) {
            ConsumerRecords<ProcessDefinitionKey, ProcessDefinitionDTO> records =
                definitionActivationConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<ProcessDefinitionKey, ProcessDefinitionDTO> record : records) {
              log.info(
                  "Received definition activation record {} to state {}",
                  record.key(),
                  record.value().getState());
              final String storedHash = storedHashes.get(record.key().getProcessDefinitionId());
              if (storedHash != null
                  && !storedHash.equals(
                      record.value().getDefinitions().getDefinitionsKey().getHash())) {
                log.warn("Hash mismatch for process definition {}", record.key());
              }
              consumeDefinitionActivation(record.key(), record.value());
            }
          }
        });
  }

  private void subscribeAndWaitUntilPartitionsAssigned(
      String prefixedTopicName, AtomicBoolean assigned) {
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
        log.error("Received {} records before expecting to received", poll.count());
      }
    }
  }

  public void cleanup() {
    if (definitionActivationConsumer != null) {
      definitionActivationConsumer.close();
    }
    consumerMap.values().forEach(KafkaConsumer::close);
  }

  void scanAndDeployBpmnDefinitions() {
    try {
      Set<Class<?>> annotatedClasses = AnnotationScanner.findAnnotatedClasses(BpmnDeployment.class);

      KafkaProducer<String, XmlDefinitionsDTO> xmlEmitter =
          new KafkaProducer<>(
              kafkaPropertiesHelper.getKafkaProducerProperties(
                  StringSerializer.class, XmlDefinitionSerializer.class));

      for (Class<?> clazz : annotatedClasses) {
        BpmnDeployment annotation = clazz.getAnnotation(BpmnDeployment.class);
        if (annotation != null) {
          Object beanInstance = InstanceProvider.getInstance(clazz);
          String resource = annotation.resource();
          URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
          Path bpmnPath = Paths.get(url.getPath());
          String xml = Files.readString(bpmnPath);
          ParsedDefinitionsDTO definitions = BpmnParser.parse(xml);

          storedHashes.put(
              definitions.getDefinitionsKey().getProcessDefinitionId(),
              definitions.getDefinitionsKey().getHash());
          definitionMap.put(definitions.getDefinitionsKey().getProcessDefinitionId(), beanInstance);
          log.info("Deploying process definition {}", definitions.getDefinitionsKey());
          xmlEmitter.send(
              new ProducerRecord<>(
                  kafkaPropertiesHelper.getPrefixedTopicName(
                      Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC),
                  definitions.getDefinitionsKey().getProcessDefinitionId(),
                  new XmlDefinitionsDTO(xml)));
        }
      }

      xmlEmitter.flush();
      xmlEmitter.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void consumeDefinitionActivation(
      ProcessDefinitionKey processDefinitionKey, ProcessDefinitionDTO value) {
    final String processDefinitionId = processDefinitionKey.getProcessDefinitionId();
    if (value.getState() == ProcessDefinitionStateEnum.ACTIVE) {
      Object workerInstance = definitionMap.get(processDefinitionId);
      if (workerInstance != null) {
        // We have a worker instance for this process definition. If no consumer exists yet
        // for that process definitionm create a new one for the external-task-trigger-incoming
        // channel
        KafkaConsumer<UUID, ExternalTaskTriggerDTO> consumer = consumerMap.get(processDefinitionId);
        if (consumer == null) {
          final KafkaConsumer<UUID, ExternalTaskTriggerDTO> newConsumer =
              createConsumer(
                  "client-consumer-" + processDefinitionId,
                  (Class<? extends Deserializer<?>>) Serdes.UUID().deserializer().getClass(),
                  ExternalTaskTriggerJsonDeserializer.class);
          consumerMap.put(processDefinitionId, newConsumer);
          newConsumer.subscribe(
              Collections.singletonList(
                  kafkaPropertiesHelper.getPrefixedTopicName(Topics.EXTERNAL_TASK_TRIGGER_TOPIC)));
          CompletableFuture.runAsync(
                  () -> {
                    while (true) {
                      ConsumerRecords<UUID, ExternalTaskTriggerDTO> records =
                          newConsumer.poll(Duration.ofMillis(100));
                      if (records.count() > 0) {
                        log.info(
                            "Received {} records for process definition {}",
                            records.count(),
                            processDefinitionId);
                        try (ExecutorService executor =
                            Executors.newVirtualThreadPerTaskExecutor()) {
                          records.forEach(
                              record ->
                                  executor.submit(
                                      () ->
                                          consumeExternalTaskTrigger(
                                              record.value(), processDefinitionId)));
                        }
                      }
                    }
                  })
              .exceptionallyAsync(
                  t -> {
                    log.error(
                        "Error while consuming external task triggers for process definition {}:{}",
                        processDefinitionId,
                        t.getMessage());
                    return null;
                  });
          int i = 0;
        }
      }
    } else if (value.getState() == ProcessDefinitionStateEnum.INACTIVE) {
      Object workerInstance = definitionMap.get(processDefinitionId);
      if (workerInstance != null) {
        // We have a worker instance. Stop consuming for this process definition
        KafkaConsumer<UUID, ExternalTaskTriggerDTO> consumer = consumerMap.get(processDefinitionId);
        if (consumer != null) {
          consumer.unsubscribe();
        }
      }
    }
  }

  private <K, V> KafkaConsumer<K, V> createConsumer(
      String groupId,
      Class<? extends Deserializer<?>> keyDeserializer,
      Class<? extends Deserializer<?>> valueDeserializer) {
    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        kafkaPropertiesHelper.getKafkaConsumerProperties(
            groupId, keyDeserializer, valueDeserializer);
    return new KafkaConsumer<>(props);
  }

  public void consumeExternalTaskTrigger(
      ExternalTaskTriggerDTO externalTaskTrigger, String definitionId) {
    String processDefinitionId =
        externalTaskTrigger.getProcessDefinitionKey().getProcessDefinitionId().split("/")[0];
    if (!definitionId.equals(processDefinitionId)) {
      return;
    }
    log.info("Processing external task trigger for process definition {}", processDefinitionId);
    String externalTaskId = externalTaskTrigger.getExternalTaskId();
    Object workerInstance = definitionMap.get(processDefinitionId);
    if (workerInstance == null) {
      respondError(
          externalTaskTrigger,
          "No worker instance found for process definition " + processDefinitionId);
      return;
    }
    // Get method from workerInstance which has matching annotation
    Class<?> aClass = workerInstance.getClass();
    Optional<Method> optMethod = findMatchingMethod(aClass, externalTaskId);
    if (optMethod.isPresent()) {
      Method method = optMethod.get();
      ExternalTaskResponseTriggerDTO processInstanceTrigger;
      try {
        Object result = method.invoke(workerInstance, getParameters(method, externalTaskTrigger));
        Map<String, JsonNode> variablesMap =
            result == null ? Map.of() : objectMapper.convertValue(result, LinkedHashMap.class);
        ExternalTaskResponseResultDTO externalTaskResponseResult =
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.SUCCESS,
                true,
                Constants.NONE,
                Constants.NONE,
                Constants.NONE);
        processInstanceTrigger =
            new ExternalTaskResponseTriggerDTO(
                externalTaskTrigger.getProcessInstanceKey(),
                externalTaskTrigger.getElementIdPath(),
                externalTaskTrigger.getElementInstanceIdPath(),
                externalTaskResponseResult,
                new VariablesDTO(variablesMap));
      } catch (EscalationEventException escalationEvent) {
        processInstanceTrigger =
            new ExternalTaskResponseTriggerDTO(
                externalTaskTrigger.getProcessInstanceKey(),
                externalTaskTrigger.getElementIdPath(),
                externalTaskTrigger.getElementInstanceIdPath(),
                new ExternalTaskResponseResultDTO(
                    ExternalTaskResponseType.ESCALATION,
                    true,
                    escalationEvent.getName(),
                    escalationEvent.getMessage(),
                    escalationEvent.getCode()),
                VariablesDTO.empty());
      } catch (Throwable e) {
        processInstanceTrigger =
            new ExternalTaskResponseTriggerDTO(
                externalTaskTrigger.getProcessInstanceKey(),
                externalTaskTrigger.getElementIdPath(),
                externalTaskTrigger.getElementInstanceIdPath(),
                new ExternalTaskResponseResultDTO(
                    ExternalTaskResponseType.ERROR,
                    true,
                    Constants.NONE,
                    e.getMessage(),
                    Constants.NONE),
                VariablesDTO.empty());
      }
      responseEmitter.send(
          new ProducerRecord<>(
              kafkaPropertiesHelper.getPrefixedTopicName(Topics.PROCESS_INSTANCE_TRIGGER_TOPIC),
              externalTaskTrigger.getProcessInstanceKey(),
              processInstanceTrigger));

    } else {
      respondError(
          externalTaskTrigger,
          "No method matching method found for external task '"
              + externalTaskId
              + "' in jobworker '"
              + aClass.getName()
              + "'");
    }
  }

  private void respondError(
      ExternalTaskTriggerDTO externalTaskTrigger, String processDefinitionId) {
    ExternalTaskResponseTriggerDTO processInstanceTrigger =
        new ExternalTaskResponseTriggerDTO(
            externalTaskTrigger.getProcessInstanceKey(),
            externalTaskTrigger.getElementIdPath(),
            externalTaskTrigger.getElementInstanceIdPath(),
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.ERROR,
                true,
                Constants.NONE,
                processDefinitionId,
                Constants.NONE),
            VariablesDTO.empty());
    responseEmitter.send(
        new ProducerRecord<>(
            kafkaPropertiesHelper.getPrefixedTopicName(Topics.PROCESS_INSTANCE_TRIGGER_TOPIC),
            externalTaskTrigger.getProcessInstanceKey(),
            processInstanceTrigger));
  }

  private Object[] getParameters(Method method, ExternalTaskTriggerDTO externalTaskTrigger) {
    // This method has the matching annotation
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    VariablesDTO variables = externalTaskTrigger.getVariables();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].getType().equals(ExternalTaskTriggerDTO.class)) {
        args[i] = externalTaskTrigger;
      } else {
        JsonNode jsonNode = variables.get(parameters[i].getName());
        // Convert jsonNode to the required type
        try {
          args[i] = objectMapper.convertValue(jsonNode, parameters[i].getType());
        } catch (IllegalArgumentException e) {
          // If the conversion fails, set the argument to null
          args[i] = null;
        }
      }
    }
    return args;
  }

  private Optional<Method> findMatchingMethod(Class<?> aClass, String externalTaskId) {
    for (Method method : aClass.getDeclaredMethods()) {
      ExternalTask externalTaskAnnotation = method.getAnnotation(ExternalTask.class);
      if (externalTaskAnnotation != null
          && externalTaskAnnotation.element().equals(externalTaskId)) {
        return Optional.of(method);
      }
    }
    if (aClass.getSuperclass() != null) {
      return findMatchingMethod(aClass.getSuperclass(), externalTaskId);
    }
    return Optional.empty();
  }

  public Map<String, Object> getDefinitionMap() {
    return definitionMap;
  }
}
