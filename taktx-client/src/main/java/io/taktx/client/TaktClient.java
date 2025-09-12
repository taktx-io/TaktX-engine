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

@Slf4j
public class TaktClient {

  @Getter private final ProcessDefinitionConsumer processDefinitionConsumer;
  @Getter private final TaktParameterResolverFactory parameterResolverFactory;
  @Getter private final ProcessInstanceResponder processInstanceResponder;

  private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
  private final ProcessDefinitionDeployer processDefinitionDeployer;
  private final ProcessInstanceProducer processInstanceProducer;
  private final ProcessInstanceUpdateConsumer processInstanceUpdateConsumer;
  private final XmlByProcessDefinitionIdConsumer xmlByProcessDefinitionIdConsumer;
  private final MessageEventSender messageEventSender;
  private final ExternalTaskTriggerTopicConsumer externalTaskTriggerTopicConsumer;
  private final UserTaskTriggerTopicConsumer userTaskTriggerTopicConsumer;
  private final ExternalTaskTopicRequester externalTaskTopicRequester;

  private TaktClient(
      TaktPropertiesHelper taktPropertiesHelper,
      ProcessInstanceResponder processInstanceResponder,
      TaktParameterResolverFactory parameterResolverFactory) {
    this.externalTaskTopicRequester = new ExternalTaskTopicRequester(taktPropertiesHelper);
    this.parameterResolverFactory = parameterResolverFactory;
    this.processDefinitionConsumer = new ProcessDefinitionConsumer(taktPropertiesHelper, executor);
    this.xmlByProcessDefinitionIdConsumer =
        new XmlByProcessDefinitionIdConsumer(taktPropertiesHelper, executor);
    this.processDefinitionDeployer = new ProcessDefinitionDeployer(taktPropertiesHelper);
    this.processInstanceProducer = new ProcessInstanceProducer(taktPropertiesHelper);
    this.messageEventSender = new MessageEventSender(taktPropertiesHelper);
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
    if (this.externalTaskTriggerTopicConsumer != null) {
      this.externalTaskTriggerTopicConsumer.stop();
    }
    this.processInstanceUpdateConsumer.stop();
    this.xmlByProcessDefinitionIdConsumer.stop();
  }

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

  /** Retrieves a process definition by its ID. */
  public Optional<ProcessDefinitionDTO> getProcessDefinitionByHash(
      String processDefinitionId, String hash) {
    return this.processDefinitionConsumer.getDeployedProcessDefinitionbyHash(
        processDefinitionId, hash);
  }

  /** Starts a process instance with the given process definition ID and variables. */
  public UUID startProcess(String process, VariablesDTO variables) {
    return processInstanceProducer.startProcess(process, variables);
  }

  /** Sends a message event to the engine. */
  public void sendMessage(MessageEventDTO messageEventDTO) {
    messageEventSender.sendMessage(messageEventDTO);
  }

  /** Registers a consumer for process instance updates. */
  public void registerInstanceUpdateConsumer(Consumer<InstanceUpdateRecord> consumer) {
    this.processInstanceUpdateConsumer.registerInstanceUpdateConsumer(consumer);
  }

  public void registerProcessDefinitionUpdateConsumer(
      BiConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> consumer) {
    this.processDefinitionConsumer.registerProcessDefinitionUpdateConsumer(consumer);
  }

  /** Deploys process definitions from classes annotated with @TaktDeployment. */
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

  /** Responds to an external task trigger. */
  public ExternalTaskInstanceResponder respondToExternalTask(
      ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return processInstanceResponder.responderForExternalTaskTrigger(externalTaskTriggerDTO);
  }

  /** Completes a user task. */
  public UserTaskInstanceResponder completeUserTask(UserTaskTriggerDTO userTaskTriggerDTO) {
    return processInstanceResponder.responderForUserTaskTrigger(userTaskTriggerDTO);
  }

  /** Terminates a process instance. */
  public void terminateElementInstance(UUID processInstanceId) {
    processInstanceProducer.terminateProcessInstance(processInstanceId);
  }

  /** Terminates an element instance within a process instance */
  public void terminateElementInstance(
      UUID activeProcessInstanceId, List<Long> elementInstanceIdPath) {
    processInstanceProducer.terminateElementInstance(
        activeProcessInstanceId, elementInstanceIdPath);
  }

  public void registerExternalTaskConsumer(
      ExternalTaskTriggerConsumer externalTaskTriggerConsumer) {
    this.externalTaskTriggerTopicConsumer.subscribeToExternalTaskTriggerTopics(
        externalTaskTriggerConsumer);
  }

  public void registerUserTaskConsumer(UserTaskTriggerConsumer userTaskTriggerConsumer) {
    this.userTaskTriggerTopicConsumer.subscribeToUserTaskTriggerTopics(userTaskTriggerConsumer);
  }

  public String getProcessDefinitionXml(ProcessDefinitionKey processDefinitionKey)
      throws IOException {
    return this.xmlByProcessDefinitionIdConsumer.getProcessDefinitionXml(processDefinitionKey);
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

    public TaktClientBuilder withTenant(String tenant) {
      this.tenant = tenant;
      return this;
    }

    public TaktClientBuilder withNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public TaktClientBuilder withKafkaProperties(Properties kafkaProperties) {
      this.kafkaProperties = kafkaProperties;
      return this;
    }
  }
}
