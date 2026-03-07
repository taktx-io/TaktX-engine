/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.CleanupPolicy;
import io.taktx.client.annotation.Deployment;
import io.taktx.client.serdes.ProcessInstanceTriggerSerializer;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.topicmanagement.ExternalTaskTopicRequester;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDSerializer;
import jakarta.annotation.Nullable;
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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;

/**
 * TaktXClient is the main entry point for interacting with the TaktX BPMN engine. It provides
 * methods to deploy process definitions, start process instances, send message events, and register
 * consumers for process definition updates, instance updates, external task triggers, and user task
 * triggers.
 */
public class TaktXClient {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(TaktXClient.class);
  private final ProcessDefinitionConsumer processDefinitionConsumer;
  private final ParameterResolverFactory parameterResolverFactory;
  private final ProcessInstanceResponder processInstanceResponder;
  private final ProcessDefinitionDeployer processDefinitionDeployer;
  private final ProcessInstanceProducer processInstanceProducer;
  private final ProcessInstanceUpdateConsumer processInstanceUpdateConsumer;
  private final XmlByProcessDefinitionIdConsumer xmlByProcessDefinitionIdConsumer;
  private final MessageEventSender messageEventSender;
  private final SignalSender signalSender;
  private final ExternalTaskTriggerTopicConsumer externalTaskTriggerTopicConsumer;
  private final UserTaskTriggerTopicConsumer userTaskTriggerTopicConsumer;
  private final ExternalTaskTopicRequester externalTaskTopicRequester;
  private final ResultProcessorFactory resultProcessorFactory;

  private TaktXClient(
      TaktPropertiesHelper taktPropertiesHelper,
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter,
      ProcessInstanceResponder processInstanceResponder,
      ParameterResolverFactory parameterResolverFactory,
      ResultProcessorFactory resultProcessorFactory) {
    Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    this.externalTaskTopicRequester = new ExternalTaskTopicRequester(taktPropertiesHelper);
    this.parameterResolverFactory = parameterResolverFactory;
    this.resultProcessorFactory = resultProcessorFactory;
    this.processDefinitionConsumer = new ProcessDefinitionConsumer(taktPropertiesHelper, executor);
    this.xmlByProcessDefinitionIdConsumer =
        new XmlByProcessDefinitionIdConsumer(taktPropertiesHelper, executor);
    this.processDefinitionDeployer = new ProcessDefinitionDeployer(taktPropertiesHelper);
    this.processInstanceProducer =
        new ProcessInstanceProducer(taktPropertiesHelper, processInstanceTriggerEmitter);
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
   * Creates a new TaktXClientBuilder instance to create a new TaktXClient.
   *
   * @return A new TaktXClientBuilder instance.
   */
  public static TaktXClientBuilder newClientBuilder() {
    return new TaktXClientBuilder();
  }

  /**
   * Starts the TaktXClient, which subscribes to process definition records and process definition
   * updates.
   */
  public void start() {
    this.processDefinitionConsumer.subscribeToDefinitionRecords();
    this.xmlByProcessDefinitionIdConsumer.subscribeToTopic();
  }

  /** Stops the TaktXClient, which unsubscribes from process definition records and process */
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
    return this.processDefinitionDeployer.deployInputStream(new String(inputStream.readAllBytes()));
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
   * Starts a new process instance of the latest version of the given process definition.
   *
   * @param process The ID of the process definition to start.
   * @param variables The initial variables for the process instance.
   * @return The UUID of the started process instance.
   */
  public UUID startProcess(String process, VariablesDTO variables) {
    return processInstanceProducer.startProcess(process, variables);
  }

  public UUID startProcess(String process, int version, VariablesDTO variables) {
    return processInstanceProducer.startProcess(process, version, variables);
  }

  /**
   * Starts a new process instance with a Platform Service authorization token.
   *
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public UUID startProcess(
      String process, int version, VariablesDTO variables, @Nullable String authorizationToken) {
    return processInstanceProducer.startProcess(process, version, variables, authorizationToken);
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
   * @param groupId The Kafka consumer group ID to use.
   * @param consumer The consumer to register.
   */
  public void registerInstanceUpdateConsumer(
      String groupId, Consumer<List<InstanceUpdateRecord>> consumer) {
    this.processInstanceUpdateConsumer.registerInstanceUpdateConsumer(groupId, consumer);
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
    Set<Deployment> deployments = AnnotationScanner.findTaktDeployments();
    for (Deployment annotation : deployments) {
      String[] resources = annotation.resources();

      String joined = String.join(",", resources);
      log.info("Deploying process definition from resource {}", joined);

      for (String resource : resources) {
        // Get the input stream for each resource, support classpath, filesystem and wildcards
        processDefinitionDeployer.deployResource(resource);
      }
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
   * Responds to an external task trigger.
   *
   * @param processInstanceId process instance id
   * @param elementInstanceIdPath the path to the element instance id
   * @return The ExternalTaskInstanceResponder to respond to the external task.
   */
  public ExternalTaskInstanceResponder respondToExternalTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return processInstanceResponder.responderForExternalTask(
        processInstanceId, elementInstanceIdPath);
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
   * Set variables in a scope.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the scope.
   * @param variables The variables to set.
   */
  public void setVariable(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    processInstanceProducer.setVariable(processInstanceId, elementInstanceIdPath, variables);
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
   * Aborts an element instance with a Platform Service authorization token.
   *
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void abortElementInstance(
      UUID activeProcessInstanceId,
      List<Long> elementInstanceIdPath,
      @Nullable String authorizationToken) {
    processInstanceProducer.abortElementInstance(
        activeProcessInstanceId, elementInstanceIdPath, authorizationToken);
  }

  /**
   * Registers an external task consumer that will be notified of external task triggers.
   *
   * @param externalTaskTriggerConsumer The external task trigger consumer to register.
   * @param groupId The group ID for the consumer.
   */
  public void registerExternalTaskConsumer(
      ExternalTaskTriggerConsumer externalTaskTriggerConsumer, String groupId) {
    this.externalTaskTriggerTopicConsumer.subscribeToExternalTaskTriggerTopics(
        externalTaskTriggerConsumer, groupId);
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
   * Gets the ProcessDefinitionConsumer instance.
   *
   * @return The ProcessDefinitionConsumer.
   */
  public ProcessDefinitionConsumer getProcessDefinitionConsumer() {
    return this.processDefinitionConsumer;
  }

  /**
   * Gets the TaktParameterResolverFactory instance.
   *
   * @return The TaktParameterResolverFactory.
   */
  public ParameterResolverFactory getParameterResolverFactory() {
    return this.parameterResolverFactory;
  }

  /**
   * Gets the ResultProcessorFactory instance.
   *
   * @return The ResultProcessorFactory.
   */
  public ResultProcessorFactory getResultProcessorFactory() {
    return resultProcessorFactory;
  }

  /**
   * Gets the ProcessInstanceResponder instance.
   *
   * @return The ProcessInstanceResponder.
   */
  public ProcessInstanceResponder getProcessInstanceResponder() {
    return this.processInstanceResponder;
  }

  /**
   * Gets the ExternalTaskTopicRequester instance.
   *
   * @return The ExternalTaskTopicRequester.
   */
  public ExternalTaskTopicRequester getExternalTaskTopicRequester() {
    return this.externalTaskTopicRequester;
  }

  /**
   * Builder class for creating TaktXClient instances. Requires NAMESPACE, and
   * KAFKA_BOOTSTRAP_SERVERS environment variables to be set or configured via the builder methods.
   */
  public static class TaktXClientBuilder {

    private Properties properties;
    private ParameterResolverFactory parameterResolverFactory;
    private ResultProcessorFactory resultProcessorFactory;

    private TaktXClientBuilder() {}

    /**
     * Builds and returns a TaktXClient instance.
     *
     * @return A TaktXClient instance.
     * @throws IllegalArgumentException if Kafka properties are not set.
     */
    public TaktXClient build() {
      if (properties == null) {
        throw new IllegalArgumentException("TaktX properties should be passed");
      }

      TaktPropertiesHelper taktPropertiesHelper = new TaktPropertiesHelper(properties);

      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter =
          new KafkaProducer<>(
              taktPropertiesHelper.getKafkaProducerProperties(
                  TaktUUIDSerializer.class, ProcessInstanceTriggerSerializer.class));

      ProcessInstanceResponder externalTaskResponder =
          new ProcessInstanceResponder(taktPropertiesHelper, processInstanceTriggerEmitter);

      ParameterResolverFactory clientParameterResolverFactory =
          this.parameterResolverFactory != null
              ? this.parameterResolverFactory
              : new DefaultParameterResolverFactory(externalTaskResponder);
      ResultProcessorFactory clientResultProcessorFactory =
          this.resultProcessorFactory != null
              ? this.resultProcessorFactory
              : new DefaultResultProcessorFactory();
      return new TaktXClient(
          taktPropertiesHelper,
          processInstanceTriggerEmitter,
          externalTaskResponder,
          clientParameterResolverFactory,
          clientResultProcessorFactory);
    }

    /**
     * Sets the TaktParameterResolverFactory to be used by the TaktXClient.
     *
     * @param parameterResolverFactory The TaktParameterResolverFactory instance.
     * @return The TaktXClientBuilder instance.
     */
    public TaktXClientBuilder withTaktParameterResolverFactory(
        ParameterResolverFactory parameterResolverFactory) {
      this.parameterResolverFactory = parameterResolverFactory;
      return this;
    }

    public TaktXClientBuilder withResultProcessorFactory(
        ResultProcessorFactory resultProcessorFactory) {
      this.resultProcessorFactory = resultProcessorFactory;
      return this;
    }

    /**
     * Sets the TaktX properties to be used by the TaktXClient.
     *
     * @param properties The TaktX properties.
     * @return The TaktXClientBuilder instance.
     */
    public TaktXClientBuilder withProperties(Properties properties) {
      this.properties = properties;
      return this;
    }
  }
}
