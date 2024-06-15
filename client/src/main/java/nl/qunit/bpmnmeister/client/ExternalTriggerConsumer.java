package nl.qunit.bpmnmeister.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseResult;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Variables;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jboss.logging.Logger;
import org.xml.sax.SAXException;

@ApplicationScoped
@Startup
public class ExternalTriggerConsumer {
  private static final Logger LOG = Logger.getLogger(ExternalTriggerConsumer.class);
  private final Map<String, KafkaConsumer<ProcessInstanceKey, ExternalTaskTrigger>> consumerMap =
      new HashMap<>();

  ObjectMapper objectMapper;
  @Inject BeanManager beanManager;
  @Inject KafkaPropertiesHelper kafkaPropertiesHelper;

  private final Map<String, Object> definitionMap = new HashMap<>();
  private KafkaConsumer<ProcessDefinitionKey, ProcessDefinition> parsedDefinitionConsumer;

  public ExternalTriggerConsumer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void init() {
    scanAndDeployBpmnDefinitions();
    parsedDefinitionConsumer =
        createConsumer(
            "client-parsed-definition-consumer",
            ProcessDefinitionKeyJsonDeserializer.class,
            ProcessDefinitionJsonDeserializer.class);
    parsedDefinitionConsumer.subscribe(
        Collections.singletonList(Topics.PROCESS_DEFINITION_PARSED_TOPIC.getTopicName()));
    CompletableFuture.runAsync(
        () -> {
          while (true) {
            ConsumerRecords<ProcessDefinitionKey, ProcessDefinition> records =
                parsedDefinitionConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<ProcessDefinitionKey, ProcessDefinition> record : records) {
              consumeDefinition(record.key());
            }
          }
        });
  }

  @PreDestroy
  public void cleanup() {
    if (parsedDefinitionConsumer != null) {
      parsedDefinitionConsumer.close();
    }
    consumerMap.values().forEach(KafkaConsumer::close);
  }

  void scanAndDeployBpmnDefinitions() {
    // Scan all beans for BpmnDeployment annotations
    try {
      Set<Bean<?>> beans = beanManager.getBeans(Object.class, new AnnotationLiteral<Any>() {});
      KafkaProducer<String, String> xmlEmitter =
          new KafkaProducer<>(
              kafkaPropertiesHelper.getKafkaProducerProperties(
                  StringSerializer.class, StringSerializer.class));

      for (Bean<?> bean : beans) {
        BpmnDeployment annotation = bean.getBeanClass().getAnnotation(BpmnDeployment.class);
        if (annotation != null) {
          Object beanInstance = getBean(bean.getBeanClass());
          String resource = annotation.resource();
          URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
          Path bpmnPath = Paths.get(url.getPath());
          String xml = Files.readString(bpmnPath);
          Definitions definitions = new BpmnParser().parse(xml);

          definitionMap.put(definitions.getDefinitionsKey().getProcessDefinitionId(), beanInstance);

          xmlEmitter.send(new ProducerRecord<>(Topics.XML_TOPIC.getTopicName(), xml));
        }
      }

      xmlEmitter.flush();
      xmlEmitter.close();

    } catch (JAXBException
        | NoSuchAlgorithmException
        | ParserConfigurationException
        | SAXException
        | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static <T> T getBean(Class<T> beanClass) {
    return CDI.current().select(beanClass).get();
  }

  public void consumeDefinition(ProcessDefinitionKey processDefinitionKey) {
    String processDefinitionId = processDefinitionKey.getProcessDefinitionId();
    Object workerInstance = definitionMap.get(processDefinitionId);
    if (workerInstance != null) {
      // We have a worker instance for this process definition. If not consumer exists yet
      // for that process definitionm create a new one for the external-task-trigger-incoming
      // channel
      KafkaConsumer<ProcessInstanceKey, ExternalTaskTrigger> consumer =
          consumerMap.get(processDefinitionId);
      if (consumer == null) {
        KafkaConsumer<ProcessInstanceKey, ExternalTaskTrigger> newConsumer =
            createConsumer(
                "consumer-" + processDefinitionId,
                ProcessInstanceKeyJsonDeserializer.class,
                ExternalTaskTriggerJsonDeserializer.class);
        consumerMap.put(processDefinitionId, newConsumer);
        newConsumer.subscribe(
            Collections.singletonList(Topics.EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName()));
        CompletableFuture.runAsync(
            () -> {
              while (true) {
                ConsumerRecords<ProcessInstanceKey, ExternalTaskTrigger> records =
                    newConsumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<ProcessInstanceKey, ExternalTaskTrigger> record : records) {
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
        externalTaskTrigger.getProcessDefinitionKey().getProcessDefinitionId();
    if (!definitionId.equals(processDefinitionId)) {
      return;
    }
    LOG.info("Received external task trigger: " + externalTaskTrigger);
    String externalTaskId = externalTaskTrigger.getElementId();
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
            KafkaProducer<ProcessInstanceKey, ExternalTaskResponseTrigger> responseEmitter =
                new KafkaProducer<>(
                    kafkaPropertiesHelper.getKafkaProducerProperties(
                        ProcessInstanceKeyJsonSeserializer.class,
                        ExternalTaskTriggerResponseSerializer.class));
            ExternalTaskResponseTrigger processInstanceTrigger;
            try {
              Object result =
                  method.invoke(workerInstance, getParameters(method, externalTaskTrigger));
              Map<String, JsonNode> variablesMap =
                  result == null
                      ? Map.of()
                      : objectMapper.convertValue(result, LinkedHashMap.class);
              ExternalTaskResponseResult externalTaskResponseResult =
                  new ExternalTaskResponseResult(true, true, null);
              processInstanceTrigger =
                  new ExternalTaskResponseTrigger(
                      externalTaskTrigger.getProcessInstanceKey(),
                      externalTaskId,
                      externalTaskResponseResult,
                      new Variables(variablesMap));
              LOG.info("Returning process instance trigger: " + processInstanceTrigger);
            } catch (Throwable e) {
              LOG.error("Error invoking method", e);
              processInstanceTrigger =
                  new ExternalTaskResponseTrigger(
                      externalTaskTrigger.getProcessInstanceKey(),
                      externalTaskId,
                      new ExternalTaskResponseResult(false, true, e.getMessage()),
                      Variables.empty());
            }
            responseEmitter.send(
                new ProducerRecord<>(
                    Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
                    externalTaskTrigger.getProcessInstanceKey(),
                    processInstanceTrigger));
            responseEmitter.flush();
            responseEmitter.close();
          });

    } else {
      throw new IllegalStateException("No method found for external task " + externalTaskId);
    }
  }

  private Object[] getParameters(Method method, ExternalTaskTrigger externalTaskTrigger) {
    // This method has the matching annotation
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    Variables variables = externalTaskTrigger.getVariables();
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
