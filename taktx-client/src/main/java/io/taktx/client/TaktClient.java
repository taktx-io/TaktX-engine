/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.CleanupPolicy;
import io.taktx.client.annotation.TaktDeployment;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.topicmanagement.ExternalTaskTopicRequester;
import io.taktx.util.TaktPropertiesHelper;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * TaktClient is the main entry point for interacting with the TaktX BPMN engine. It provides
 * methods to deploy process definitions, start process instances, send message events, and register
 * consumers for process definition updates, instance updates, external task triggers, and user task
 * triggers.
 */
@Slf4j
public class TaktClient {

  @Getter private final ProcessDefinitionConsumer processDefinitionConsumer;
  @Getter private final TaktParameterResolverFactory parameterResolverFactory;
  @Getter private final ProcessInstanceResponder processInstanceResponder;

  private final ProcessDefinitionDeployer processDefinitionDeployer;
  private final ProcessInstanceProducer processInstanceProducer;
  private final ProcessInstanceUpdateConsumer processInstanceUpdateConsumer;
  private final XmlByProcessDefinitionIdConsumer xmlByProcessDefinitionIdConsumer;
  private final MessageEventSender messageEventSender;
  private final SignalSender signalSender;
  private final ExternalTaskTriggerTopicConsumer externalTaskTriggerTopicConsumer;
  private final UserTaskTriggerTopicConsumer userTaskTriggerTopicConsumer;
  private final ExternalTaskTopicRequester externalTaskTopicRequester;

  private TaktClient(
      TaktPropertiesHelper taktPropertiesHelper,
      ProcessInstanceResponder processInstanceResponder,
      TaktParameterResolverFactory parameterResolverFactory) {
    Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    this.externalTaskTopicRequester = new ExternalTaskTopicRequester(taktPropertiesHelper);
    this.parameterResolverFactory = parameterResolverFactory;
    this.processDefinitionConsumer = new ProcessDefinitionConsumer(taktPropertiesHelper, executor);
    this.xmlByProcessDefinitionIdConsumer =
        new XmlByProcessDefinitionIdConsumer(taktPropertiesHelper, executor);
    this.processDefinitionDeployer = new ProcessDefinitionDeployer(taktPropertiesHelper);
    this.processInstanceProducer = new ProcessInstanceProducer(taktPropertiesHelper);
    this.messageEventSender = new MessageEventSender(taktPropertiesHelper);
    this.signalSender = new SignalSender(taktPropertiesHelper);
    this.processInstanceUpdateConsumer =
        new ProcessInstanceUpdateConsumer(taktPropertiesHelper, executor);
    this.processInstanceResponder = processInstanceResponder;
    this.externalTaskTriggerTopicConsumer =
        new ExternalTaskTriggerTopicConsumer(taktPropertiesHelper, executor);
    this.userTaskTriggerTopicConsumer =
        new UserTaskTriggerTopicConsumer(taktPropertiesHelper, executor);
  }

  /**
   * Creates a new TaktClientBuilder instance to create a new TaktClient.
   *
   * @return A new TaktClientBuilder instance.
   */
  public static TaktClientBuilder newClientBuilder() {
    return new TaktClientBuilder();
  }

  /**
   * Starts the TaktClient, which subscribes to process definition records and process definition
   * updates.
   */
  public void start() {
    this.processDefinitionConsumer.subscribeToDefinitionRecords();
    this.xmlByProcessDefinitionIdConsumer.subscribeToTopic();
  }

  /** Stops the TaktClient, which unsubscribes from process definition records and process */
  public void stop() {
    this.processDefinitionConsumer.stop();
    this.externalTaskTriggerTopicConsumer.stop();
    this.processInstanceUpdateConsumer.stop();
    this.xmlByProcessDefinitionIdConsumer.stop();
  }

  /**
   * Requests the creation of a Kafka topic for an external task.
   *
   * @param externalTaskId The ID of the external task.
   * @param partitions The number of partitions for the topic.
   * @param cleanupPolicy The cleanup policy for the topic.
   * @param replicationFactor The replication factor for the topic.
   * @return The name of the created topic.
   */
  public String requestExternalTaskTopic(
      String externalTaskId, int partitions, CleanupPolicy cleanupPolicy, short replicationFactor) {
    return this.externalTaskTopicRequester.requestExternalTaskTopic(
        externalTaskId, partitions, cleanupPolicy, replicationFactor);
  }

  /**
   * Deploys a process definition from an InputStream.
   *
   * @param inputStream The InputStream containing the process definition XML.
   * @return The parsed definitions DTO.
   * @throws IOException If an error occurs while reading the InputStream.
   */
  public ParsedDefinitionsDTO deployProcessDefinition(InputStream inputStream) throws IOException {
    return this.processDefinitionDeployer.deploy(new String(inputStream.readAllBytes()));
  }

  /**
   * Retrieves a deployed process definition by its ID and hash.
   *
   * @param processDefinitionId The ID of the process definition.
   * @param hash The hash of the process definition.
   * @return An Optional containing the ProcessDefinitionDTO if found, or empty if not found.
   */
  public Optional<ProcessDefinitionDTO> getProcessDefinitionByHash(
      String processDefinitionId, String hash) {
    return this.processDefinitionConsumer.getDeployedProcessDefinitionbyHash(
        processDefinitionId, hash);
  }

  /**
   * Starts a new process instance.
   *
   * @param process The ID of the process definition to start.
   * @param variables The initial variables for the process instance.
   * @return The UUID of the started process instance.
   */
  public UUID startProcess(String process, VariablesDTO variables) {
    return processInstanceProducer.startProcess(process, variables);
  }

  /**
   * Sends a message event to the engine.
   *
   * @param messageEventDTO The message event DTO containing the message details.
   */
  public void sendMessage(MessageEventDTO messageEventDTO) {
    messageEventSender.sendMessage(messageEventDTO);
  }

  /**
   * Registers a consumer that will be notified of instance update records.
   *
   * @param consumer The consumer to register.
   */
  public void registerInstanceUpdateConsumer(Consumer<InstanceUpdateRecord> consumer) {
    this.processInstanceUpdateConsumer.registerInstanceUpdateConsumer(consumer);
  }

  /**
   * Registers a consumer that will be notified of process definition updates.
   *
   * @param consumer The consumer to register.
   */
  public void registerProcessDefinitionUpdateConsumer(
      BiConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> consumer) {
    this.processDefinitionConsumer.registerProcessDefinitionUpdateConsumer(consumer);
  }

  /** Deploys all classes annotated with @TaktDeployment found in the classpath. */
  public void deployTaktDeploymentAnnotatedClasses() {
    try {
      Set<TaktDeployment> taktDeployments = AnnotationScanner.findTaktDeployments();
      for (TaktDeployment annotation : taktDeployments) {
        String resource = annotation.resource();
        log.info("Deploying process definition from resource {}", resource);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource);
        if (inputStream == null) {
          throw new FileNotFoundException("Resource not found: " + resource);
        }

        ParsedDefinitionsDTO parsedDefinitionsDTO = deployProcessDefinition(inputStream);
        log.info("Deploying process definition {}", parsedDefinitionsDTO.getDefinitionsKey());
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Responds to an external task trigger.
   *
   * @param externalTaskTriggerDTO The external task trigger DTO.
   * @return The ExternalTaskInstanceResponder to respond to the external task.
   */
  public ExternalTaskInstanceResponder respondToExternalTask(
      ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return processInstanceResponder.responderForExternalTaskTrigger(externalTaskTriggerDTO);
  }

  /**
   * Completes a user task.
   *
   * @param userTaskTriggerDTO The user task trigger DTO.
   * @return The UserTaskInstanceResponder to respond to the user task.
   */
  public UserTaskInstanceResponder completeUserTask(UserTaskTriggerDTO userTaskTriggerDTO) {
    return processInstanceResponder.responderForUserTaskTrigger(userTaskTriggerDTO);
  }

  /**
   * Terminates a process instance.
   *
   * @param processInstanceId The UUID of the process instance to terminate.
   */
  public void abortElementInstance(UUID processInstanceId) {
    processInstanceProducer.abortProcessInstance(processInstanceId);
  }

  /**
   * Aborts a specific element instance within a process instance.
   *
   * @param activeProcessInstanceId The UUID of the active process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the element to abort.
   */
  public void abortElementInstance(UUID activeProcessInstanceId, List<Long> elementInstanceIdPath) {
    processInstanceProducer.abortElementInstance(activeProcessInstanceId, elementInstanceIdPath);
  }

  /**
   * Registers an external task consumer that will be notified of external task triggers.
   *
   * @param externalTaskTriggerConsumer The external task trigger consumer to register.
   * @param gruopId The group ID for the consumer.
   */
  public void registerExternalTaskConsumer(
      ExternalTaskTriggerConsumer externalTaskTriggerConsumer, String gruopId) {
    this.externalTaskTriggerTopicConsumer.subscribeToExternalTaskTriggerTopics(
        externalTaskTriggerConsumer, gruopId);
  }

  /**
   * Registers a user task consumer that will be notified of user task triggers.
   *
   * @param userTaskTriggerConsumer The user task trigger consumer to register.
   */
  public void registerUserTaskConsumer(UserTaskTriggerConsumer userTaskTriggerConsumer) {
    this.userTaskTriggerTopicConsumer.subscribeToUserTaskTriggerTopics(userTaskTriggerConsumer);
  }

  /**
   * Retrieves the XML of a process definition by its key.
   *
   * @param processDefinitionKey The key of the process definition.
   * @return The XML of the process definition.
   * @throws IOException If an error occurs while retrieving the XML.
   */
  public String getProcessDefinitionXml(ProcessDefinitionKey processDefinitionKey)
      throws IOException {
    return this.xmlByProcessDefinitionIdConsumer.getProcessDefinitionXml(processDefinitionKey);
  }

  /**
   * Sends a signal event to the engine.
   *
   * @param signalName The name of the signal to send.
   */
  public void sendSignal(String signalName) {
    this.signalSender.sendMSignal(new SignalDTO(signalName));
  }

  /**
   * Builder class for creating TaktClient instances. Requires TENANT, NAMESPACE, and
   * KAFKA_BOOTSTRAP_SERVERS environment variables to be set or configured via the builder methods.
   */
  public static class TaktClientBuilder {

    private String tenant;
    private String namespace;
    private Properties kafkaProperties;

    private TaktClientBuilder() {
      this.tenant = System.getenv("TENANT");
      this.namespace = System.getenv("NAMESPACE");
    }

    /**
     * Builds and returns a TaktClient instance.
     *
     * @return A TaktClient instance.
     * @throws IllegalArgumentException if TENANT, NAMESPACE, or Kafka properties are not set.
     */
    public TaktClient build() {
      if (tenant == null) {
        throw new IllegalArgumentException("TENANT environment variable is not set");
      }
      if (namespace == null) {
        throw new IllegalArgumentException("NAMESPACE environment variable is not set");
      }
      if (kafkaProperties == null) {
        throw new IllegalArgumentException("Kakfa properties should be passed");
      }

      TaktPropertiesHelper taktPropertiesHelper =
          new TaktPropertiesHelper(tenant, namespace, kafkaProperties);

      ProcessInstanceResponder externalTaskResponder =
          new ProcessInstanceResponder(taktPropertiesHelper);

      TaktParameterResolverFactory parameterResolverFactory =
          new DefaultTaktParameterResolverFactory(externalTaskResponder);

      return new TaktClient(taktPropertiesHelper, externalTaskResponder, parameterResolverFactory);
    }

    /**
     * Sets the tenant for the TaktClient.
     *
     * @param tenant The tenant to set.
     * @return The TaktClientBuilder instance.
     */
    public TaktClientBuilder withTenant(String tenant) {
      this.tenant = tenant;
      return this;
    }

    /**
     * Sets the namespace for the TaktClient.
     *
     * @param namespace The namespace to set.
     * @return The TaktClientBuilder instance.
     */
    public TaktClientBuilder withNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    /**
     * Sets the Kafka properties for the TaktClient.
     *
     * @param kafkaProperties The Kafka properties to set.
     * @return The TaktClientBuilder instance.
     */
    public TaktClientBuilder withKafkaProperties(Properties kafkaProperties) {
      this.kafkaProperties = kafkaProperties;
      return this;
    }
  }
}
