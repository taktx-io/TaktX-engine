package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseResult;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTypeEnum;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringSerializer;


public class ExternalTriggerConsumer {
  private final Map<String, KafkaConsumer<UUID, ExternalTaskTrigger>> consumerMap = new HashMap<>();

  private final ObjectMapper objectMapper;
  private final KafkaPropertiesHelper kafkaPropertiesHelper;

  private final Map<String, Object> definitionMap = new HashMap<>();
  private KafkaConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> parsedDefinitionConsumer;
  private KafkaProducer<UUID, ExternalTaskResponseTrigger> responseEmitter;

  ExternalTriggerConsumer(KafkaPropertiesHelper kafkaPropertiesHelper) {
    this.objectMapper = new ObjectMapper();
    this.kafkaPropertiesHelper = kafkaPropertiesHelper;
  }

  void init() {
    subscribeToDefinitionRecords();
    scanAndDeployBpmnDefinitions();
  }

  private void subscribeToDefinitionRecords() {
    parsedDefinitionConsumer =
        createConsumer(
            "client-parsed-definition-consumer",
            ProcessDefinitionKeyJsonDeserializer.class,
            ProcessDefinitionJsonDeserializer.class);

    String prefixedTopicName =
        kafkaPropertiesHelper.getPrefixedTopicName(Topics.PROCESS_DEFINITION_PARSED_TOPIC);
    parsedDefinitionConsumer.subscribe(Collections.singletonList(prefixedTopicName));

    responseEmitter =
        new KafkaProducer<>(
            kafkaPropertiesHelper.getKafkaProducerProperties(
                ProcessInstanceKeyJsonSeserializer.class,
                ExternalTaskTriggerResponseSerializer.class));

    CompletableFuture.runAsync(
        () -> {
          while (true) {
            ConsumerRecords<ProcessDefinitionKey, ProcessDefinitionDTO> records =
                parsedDefinitionConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<ProcessDefinitionKey, ProcessDefinitionDTO> record : records) {
              consumeDefinition(record.key());
            }
          }
        });
  }

  public void cleanup() {
    if (parsedDefinitionConsumer != null) {
      parsedDefinitionConsumer.close();
    }
    consumerMap.values().forEach(KafkaConsumer::close);
  }

  void scanAndDeployBpmnDefinitions() {
    try {
      Set<Class<?>> annotatedClasses = AnnotationScanner.findAnnotatedClasses(BpmnDeployment.class);

      KafkaProducer<String, String> xmlEmitter =
          new KafkaProducer<>(
              kafkaPropertiesHelper.getKafkaProducerProperties(
                  StringSerializer.class, StringSerializer.class));

      for (Class<?> clazz : annotatedClasses) {
        BpmnDeployment annotation = clazz.getAnnotation(BpmnDeployment.class);
        if (annotation != null) {
          Object beanInstance = InstanceProvider.getInstance(clazz);
          String resource = annotation.resource();
          URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
          Path bpmnPath = Paths.get(url.getPath());
          String xml = Files.readString(bpmnPath);
          DefinitionsDTO definitions = new BpmnParser().parse(xml);

          definitionMap.put(definitions.getDefinitionsKey().getProcessDefinitionId(), beanInstance);
          xmlEmitter.send(
              new ProducerRecord<>(
                  kafkaPropertiesHelper.getPrefixedTopicName(Topics.XML_TOPIC),
                  definitions.getDefinitionsKey().getProcessDefinitionId(),
                  xml));
        }
      }

      xmlEmitter.flush();
      xmlEmitter.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void consumeDefinition(ProcessDefinitionKey processDefinitionKey) {
    String processDefinitionId = processDefinitionKey.getProcessDefinitionId().split("/")[0];
    Object workerInstance = definitionMap.get(processDefinitionId);
    if (workerInstance != null) {
      // We have a worker instance for this process definition. If not consumer exists yet
      // for that process definitionm create a new one for the external-task-trigger-incoming
      // channel
      KafkaConsumer<UUID, ExternalTaskTrigger> consumer = consumerMap.get(processDefinitionId);
      if (consumer == null) {
        KafkaConsumer<UUID, ExternalTaskTrigger> newConsumer =
            createConsumer(
                "consumer-" + processDefinitionId,
                ProcessInstanceKeyJsonDeserializer.class,
                ExternalTaskTriggerJsonDeserializer.class);
        consumerMap.put(processDefinitionId, newConsumer);
        newConsumer.subscribe(
            Collections.singletonList(
                kafkaPropertiesHelper.getPrefixedTopicName(Topics.EXTERNAL_TASK_TRIGGER_TOPIC)));
        CompletableFuture.runAsync(
            () -> {
              while (true) {
                ConsumerRecords<UUID, ExternalTaskTrigger> records =
                    newConsumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<UUID, ExternalTaskTrigger> record : records) {
                  consumeExternalTaskTrigger(record.value(), processDefinitionId);
                }
              }
            });
      }
    }
  }

  private <K, V> KafkaConsumer<K, V> createConsumer(
      String groupId,
      Class<? extends Deserializer<?>> keyDeserializer,
      Class<? extends Deserializer<?>> valueDeserializer) {
    Properties props =
        kafkaPropertiesHelper.getKafkaConsumerProperties(
            groupId, keyDeserializer, valueDeserializer);
    return new KafkaConsumer<>(props);
  }

  public void consumeExternalTaskTrigger(
      ExternalTaskTrigger externalTaskTrigger, String definitionId) {
    String processDefinitionId =
        externalTaskTrigger.getProcessDefinitionKey().getProcessDefinitionId().split("/")[0];
    if (!definitionId.equals(processDefinitionId)) {
      return;
    }
    String externalTaskId = externalTaskTrigger.getExternalTaskId();
    Object workerInstance = definitionMap.get(processDefinitionId);
    if (workerInstance == null) {
      return;
    }
    // Get method from workerInstance which has matching annotation
    Class<?> aClass = workerInstance.getClass();
    Optional<Method> optMethod = findMatchingMethod(aClass, externalTaskId);
    if (optMethod.isPresent()) {
      Method method = optMethod.get();

      CompletableFuture.runAsync(
          () -> {
            ExternalTaskResponseTrigger processInstanceTrigger;
            try {
              Object result =
                  method.invoke(workerInstance, getParameters(method, externalTaskTrigger));
              Map<String, JsonNode> variablesMap =
                  result == null
                      ? Map.of()
                      : objectMapper.convertValue(result, LinkedHashMap.class);
              ExternalTaskResponseResult externalTaskResponseResult =
                  new ExternalTaskResponseResult(
                      ExternalTaskResponseTypeEnum.SUCCESS,
                      true,
                      Constants.NONE,
                      Constants.NONE,
                      Constants.NONE);
              processInstanceTrigger =
                  new ExternalTaskResponseTrigger(
                      externalTaskTrigger.getProcessInstanceKey(),
                      externalTaskTrigger.getElementIdPath(),
                      externalTaskTrigger.getElementInstanceIdPath(),
                      externalTaskResponseResult,
                      new VariablesDTO(variablesMap));
            } catch (EscalationEventException escalationEvent) {
              processInstanceTrigger =
                  new ExternalTaskResponseTrigger(
                      externalTaskTrigger.getProcessInstanceKey(),
                      externalTaskTrigger.getElementIdPath(),
                      externalTaskTrigger.getElementInstanceIdPath(),
                      new ExternalTaskResponseResult(
                          ExternalTaskResponseTypeEnum.ESCALATION,
                          true,
                          escalationEvent.getName(),
                          escalationEvent.getMessage(),
                          escalationEvent.getCode()),
                      VariablesDTO.empty());
            } catch (Throwable e) {
              processInstanceTrigger =
                  new ExternalTaskResponseTrigger(
                      externalTaskTrigger.getProcessInstanceKey(),
                      externalTaskTrigger.getElementIdPath(),
                      externalTaskTrigger.getElementInstanceIdPath(),
                      new ExternalTaskResponseResult(
                          ExternalTaskResponseTypeEnum.ERROR,
                          true,
                          Constants.NONE,
                          e.getMessage(),
                          Constants.NONE),
                      VariablesDTO.empty());
            }
            responseEmitter.send(
                new ProducerRecord<>(
                    kafkaPropertiesHelper.getPrefixedTopicName(
                        Topics.PROCESS_INSTANCE_TRIGGER_TOPIC),
                    externalTaskTrigger.getProcessInstanceKey(),
                    processInstanceTrigger));
          });

    } else {
      throw new IllegalStateException("No method found for external task " + externalTaskId);
    }
  }

  private Object[] getParameters(Method method, ExternalTaskTrigger externalTaskTrigger) {
    // This method has the matching annotation
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    VariablesDTO variables = externalTaskTrigger.getVariables();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].getType().equals(ExternalTaskTrigger.class)) {
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
