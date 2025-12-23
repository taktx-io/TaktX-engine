/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.CleanupPolicy;
import io.taktx.Topics;
import io.taktx.client.ExternalTaskTriggerConsumer;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.TaktXClient;
import io.taktx.client.UserTaskTriggerConsumer;
import io.taktx.client.serdes.TopicMetaJsonDeserializer;
import io.taktx.dto.ActivityInstanceDTO;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.FlowElementsDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.NewInstanceSignalSubscriptionDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.SubProcessDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.generic.MutableClock;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.pi.testengine.AdminClientHelper.ConsumerLagInfo;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

public class BpmnTestEngine {
  private static final Logger LOG = Logger.getLogger(BpmnTestEngine.class);
  public static final Duration DEFAULT_DURATION = Duration.ofSeconds(10);
  public static final String TOPIC_TEST_PREFIX = "namespace.";
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(BpmnTestEngine.class);

  private TaktXClient taktClient;

  private final Map<UUID, Set<UUID>> processInstanceParentChildMap = new ConcurrentHashMap<>();
  private final Map<UUID, ConcurrentLinkedQueue<UserTaskTriggerDTO>> userTaskTriggerQueueMap =
      new ConcurrentHashMap<>();
  private final Map<UUID, ProcessInstanceDTO> processInstanceMap = new ConcurrentHashMap<>();
  private final Map<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceMap =
      new ConcurrentHashMap<>();
  private final Map<UUID, VariablesDTO> variablesMap = new ConcurrentHashMap<>();
  private final Map<String, ConcurrentLinkedQueue<MessageEventDTO>> messageSubscriptionMap =
      new ConcurrentHashMap<>();
  private final Map<String, ConcurrentLinkedQueue<SignalDTO>> signalMap = new ConcurrentHashMap<>();
  private ProcessDefinitionDTO activeProcessDefintion;
  private UUID activeProcessInstanceId;
  private final Map<UUID, Map<String, ExternalTaskTriggerDTO>> activeExternalTaskTriggers =
      new ConcurrentHashMap<>();
  private UserTaskTriggerDTO activeUserTaskTrigger;
  private ParsedDefinitionsDTO definitionsBeingDeployed;
  private final Clock originalClock;
  private final MutableClock mutableClock;
  private UUID latestInstantiatedProcessInstanceId;
  private KafkaConsumerUtil<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerConsumer;
  private KafkaConsumerUtil<MessageEventKeyDTO, MessageEventDTO> messageEventConsumer;
  private KafkaConsumerUtil<String, SignalDTO> signalConsumer;
  private KafkaConsumerUtil<String, TopicMetaDTO> actualTopicMetaConsumer;
  private FlowNodeInstanceDTO selectedFlowNodeInstance;
  private final Map<String, List<String>> elementIdIndexMap = new HashMap<>();
  private final Map<String, TopicMetaDTO> topicMetaCache = new ConcurrentHashMap<>();
  private AdminClientHelper adminClientHelper;

  public BpmnTestEngine(Clock clock) {
    this.originalClock = Clock.fixed(clock.instant(), clock.getZone());
    this.mutableClock = (MutableClock) clock;
  }

  private static @NotNull ProcessInstanceDTO getProcessInstanceDTO(
      UUID processInstanceId, ProcessInstanceUpdateDTO processInstanceUpdate) {
    return new ProcessInstanceDTO(
        processInstanceId,
        processInstanceUpdate.getParentProcessInstanceId(),
        processInstanceUpdate.getScope(),
        processInstanceUpdate.getParentElementInstancePath(),
        processInstanceUpdate.getProcessDefinitionKey(),
        false,
        Set.of(),
        processInstanceUpdate.getIncidentInfoDTO());
  }

  public void close() {
    LOG.info("Closing bpmn test engine");
    taktClient.stop();
    processInstanceTriggerConsumer.stop();
    messageEventConsumer.stop();
    signalConsumer.stop();
    actualTopicMetaConsumer.stop();
  }

  public void init() {
    String kafkaBootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);

    adminClientHelper = new AdminClientHelper(kafkaBootstrapServers);
    // Wait for Kafka broker to be available before proceeding
    adminClientHelper.waitForKafkaBroker(kafkaBootstrapServers);

    Properties kakaProperties = new Properties();
    kakaProperties.put("bootstrap.servers", kafkaBootstrapServers);
    kakaProperties.put("taktx.engine.namespace", "namespace");
    kakaProperties.put("taktx.external.task.consumer.threads", 2);

    taktClient = TaktXClient.newClientBuilder().withProperties(kakaProperties).build();
    taktClient.registerInstanceUpdateConsumer("bpmntestengine", BpmnTestEngine.this::consume);
    taktClient.registerUserTaskConsumer(BpmnTestEngine.this::consumeUserTaskTrigger);
    taktClient.start();

    processInstanceTriggerConsumer =
        new KafkaConsumerUtil<>(
            "test-group",
            TOPIC_TEST_PREFIX + Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
            ProcessInstanceTriggerDeserializer.class.getName(),
            this::consumeProcessInstanceTrigger);
    messageEventConsumer =
        new KafkaConsumerUtil<>(
            "test-group",
            TOPIC_TEST_PREFIX + Topics.MESSAGE_EVENT_TOPIC.getTopicName(),
            MessageEventKeyDeserializer.class.getName(),
            MessageEventDeserializer.class.getName(),
            this::consumeMessageEvent);
    signalConsumer =
        new KafkaConsumerUtil<>(
            "test-group",
            TOPIC_TEST_PREFIX + Topics.SIGNAL_TOPIC.getTopicName(),
            StringDeserializer.class.getName(),
            SignalDeserializer.class.getName(),
            this::consumeSignal);
    actualTopicMetaConsumer =
        new KafkaConsumerUtil<>(
            "test-group",
            TOPIC_TEST_PREFIX + Topics.TOPIC_META_ACTUAL_TOPIC.getTopicName(),
            StringDeserializer.class.getName(),
            TopicMetaJsonDeserializer.class.getName(),
            this::consumeTopicMeta);
  }

  public void consumeProcessInstanceTrigger(
      ConsumerRecord<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerRecord) {
    ProcessInstanceTriggerDTO trigger = processInstanceTriggerRecord.value();
    LOG.info(
        "Received process ProcessInstanceTrigger trigger: "
            + trigger
            + " "
            + trigger.getClass().getName());
    if (trigger instanceof StartCommandDTO startCommand
        && startCommand.getParentProcessInstanceId() != null) {
      Set<UUID> uuids1 =
          processInstanceParentChildMap.computeIfAbsent(
              startCommand.getParentProcessInstanceId(), k -> new ConcurrentSkipListSet<>());
      uuids1.add(startCommand.getProcessInstanceId());
    }
  }

  public void consumeMessageEvent(
      ConsumerRecord<MessageEventKeyDTO, MessageEventDTO> messageEventRecord) {
    MessageEventDTO messageEvent = messageEventRecord.value();
    LOG.info("Received message event: {}" + messageEvent);
    ConcurrentLinkedQueue<MessageEventDTO> messageEvents =
        messageSubscriptionMap.computeIfAbsent(
            messageEvent.getMessageName(), k -> new ConcurrentLinkedQueue<>());
    messageEvents.add(messageEvent);
  }

  public void consumeSignal(ConsumerRecord<String, SignalDTO> signalRecord) {
    SignalDTO signal = signalRecord.value();
    LOG.info("Received signal: {}" + signal);
    ConcurrentLinkedQueue<SignalDTO> signals =
        signalMap.computeIfAbsent(signal.getSignalName(), k -> new ConcurrentLinkedQueue<>());
    signals.add(signal);
  }

  public void consumeTopicMeta(ConsumerRecord<String, TopicMetaDTO> topicMetaRecord) {
    LOG.info("Received topic meta event: {}" + topicMetaRecord.value());
    TopicMetaDTO topicMetaDTO = topicMetaRecord.value();
    topicMetaCache.put(topicMetaDTO.getTopicName(), topicMetaDTO);
  }

  public void consumeExternalTaskTrigger(ExternalTaskTriggerDTO externalTaskTrigger) {

    LOG.info("Received external task trigger: " + externalTaskTrigger);

    Map<String, ExternalTaskTriggerDTO> externalTaskTriggers =
        activeExternalTaskTriggers.computeIfAbsent(
            externalTaskTrigger.getProcessInstanceId(), k -> new ConcurrentHashMap<>());
    externalTaskTriggers.put(externalTaskTrigger.getExternalTaskId(), externalTaskTrigger);

    LOG.info(
        "Addewd external task trigger "
            + externalTaskTrigger.getExternalTaskId()
            + " for process id "
            + externalTaskTrigger.getProcessInstanceId());
  }

  public void consumeUserTaskTrigger(UserTaskTriggerDTO userTaskTrigger) {

    LOG.info("Received user task trigger: " + userTaskTrigger);

    ConcurrentLinkedQueue<UserTaskTriggerDTO> uerTaskTriggers =
        userTaskTriggerQueueMap.computeIfAbsent(
            userTaskTrigger.getProcessInstanceId(), k -> new ConcurrentLinkedQueue<>());
    uerTaskTriggers.add(userTaskTrigger);
    LOG.info(
        "User task triggers: "
            + userTaskTriggerQueueMap
            + " "
            + System.identityHashCode(userTaskTriggerQueueMap));
  }

  public void consume(List<InstanceUpdateRecord> instanceUpdateRecords) {
    for (InstanceUpdateRecord instanceUpdateRecord : instanceUpdateRecords) {
      if (instanceUpdateRecord.getUpdate()
          instanceof ProcessInstanceUpdateDTO processInstanceUpdate) {
        LOG.info(
            "Received process instanceToContinue update: "
                + instanceUpdateRecord.getProcessInstanceId()
                + " "
                + instanceUpdateRecord.getUpdate());

        ProcessInstanceDTO processInstanceDTO =
            getProcessInstanceDTO(
                instanceUpdateRecord.getProcessInstanceId(), processInstanceUpdate);
        log.info(
            "Adding to process instanceToContinue map {} {}",
            System.identityHashCode(processInstanceMap),
            processInstanceDTO);
        ProcessInstanceDTO previousProcessInstance =
            processInstanceMap.put(instanceUpdateRecord.getProcessInstanceId(), processInstanceDTO);
        if (previousProcessInstance == null) {
          latestInstantiatedProcessInstanceId = processInstanceDTO.getProcessInstanceId();
        }

        VariablesDTO existingVariables =
            variablesMap.computeIfAbsent(
                instanceUpdateRecord.getProcessInstanceId(), k -> VariablesDTO.empty());
        existingVariables
            .getVariables()
            .putAll(processInstanceUpdate.getVariables().getVariables());

      } else if (instanceUpdateRecord.getUpdate()
          instanceof FlowNodeInstanceUpdateDTO flowNodeInstanceUpdate) {
        LOG.info("Received FlowNode instanceToContinue update: " + instanceUpdateRecord);

        FlowNodeInstanceKeyDTO key =
            new FlowNodeInstanceKeyDTO(
                instanceUpdateRecord.getProcessInstanceId(),
                flowNodeInstanceUpdate.getFlowNodeInstancePath());
        this.flowNodeInstanceMap.put(key, flowNodeInstanceUpdate.getFlowNodeInstance());

        VariablesDTO existingVariables =
            variablesMap.computeIfAbsent(key.getProcessInstanceId(), k -> VariablesDTO.empty());
        existingVariables
            .getVariables()
            .putAll(flowNodeInstanceUpdate.getVariables().getVariables());
      }
    }
  }

  public UserTaskTriggerDTO pollUserTask() {
    ConcurrentLinkedQueue<UserTaskTriggerDTO> externalTaskTriggers =
        userTaskTriggerQueueMap.get(activeProcessInstanceId);
    log.info("polled user task queue {} {}", activeProcessInstanceId, externalTaskTriggers);
    if (externalTaskTriggers == null) {
      return null;
    }
    UserTaskTriggerDTO poll = externalTaskTriggers.poll();
    log.info("polled user task queue {} {}", externalTaskTriggers, poll);
    return poll;
  }

  public BpmnTestEngine deployProcessDefinition(String filename) throws IOException {
    LOG.info("Deploying process definition: " + filename);
    definitionsBeingDeployed =
        taktClient.deployProcessDefinition(BpmnTestEngine.class.getResourceAsStream(filename));

    return this;
  }

  public BpmnTestEngine registerAndSubscribeToExternalTaskIds(String... externalTaskIds) {
    LOG.info(
        "Registering and subscribing to external task ids: " + String.join(", ", externalTaskIds));
    registerTopicsForExternalTasks(externalTaskIds);
    subscribeToExternalTaskTopics(externalTaskIds);
    return this;
  }

  public BpmnTestEngine waitForProcessDeployment() {
    return this.waitForProcessDeployment(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitForProcessDeployment(Duration duration) {
    String processDefinitionId =
        definitionsBeingDeployed.getDefinitionsKey().getProcessDefinitionId();

    activeProcessDefintion =
        Awaitility.await()
            .atMost(duration)
            .until(
                () ->
                    taktClient
                        .getProcessDefinitionByHash(
                            processDefinitionId,
                            definitionsBeingDeployed.getDefinitionsKey().getHash())
                        .orElse(null),
                Objects::nonNull);

    List<String> indexList =
        indexProcessDefinition(
            activeProcessDefintion.getDefinitions().getRootProcess().getFlowElements());

    indexList.sort(String::compareTo);
    elementIdIndexMap.put(
        activeProcessDefintion.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
        indexList);
    return this;
  }

  private List<String> indexProcessDefinition(FlowElementsDTO flowElementsDTO) {
    List<String> elementIdIndex = new ArrayList<>();
    flowElementsDTO
        .getElements()
        .forEach(
            (key, value) -> {
              elementIdIndex.add(key);
              if (value instanceof SubProcessDTO subProcessDTO) {
                elementIdIndex.addAll(indexProcessDefinition(subProcessDTO.getElements()));
              }
            });
    return elementIdIndex;
  }

  public BpmnTestEngine deployProcessDefinitionAndWait(String filename) throws IOException {
    return deployProcessDefinitionAndWait(filename, DEFAULT_DURATION);
  }

  public BpmnTestEngine deployProcessDefinitionAndWait(String filename, Duration duration)
      throws IOException {
    deployProcessDefinition(filename);
    waitForProcessDeployment(duration);
    return this;
  }

  public void subscribeToUserTaskTopic() {
    taktClient.registerUserTaskConsumer(
        new UserTaskTriggerConsumer() {
          @Override
          public void accept(UserTaskTriggerDTO value) {
            BpmnTestEngine.this.consumeUserTaskTrigger(value);
          }
        });
  }

  private void subscribeToExternalTaskTopics(String[] externalTaskIds) {
    if (externalTaskIds.length > 0) {
      taktClient.registerExternalTaskConsumer(
          new ExternalTaskTriggerConsumer() {
            @Override
            public Set<String> getJobIds() {
              return Set.of(externalTaskIds);
            }

            @Override
            public void acceptBatch(List<ExternalTaskTriggerDTO> batch) {
              for (ExternalTaskTriggerDTO task : batch) {
                BpmnTestEngine.this.consumeExternalTaskTrigger(task);
              }
            }
          },
          "bpmn-test-engine-external-task-trigger-consumer-" + UUID.randomUUID().toString());
    }
  }

  private void registerTopicsForExternalTasks(String... externalTaskIds) {
    List<String> topics = new ArrayList<>();

    for (String externalTaskId : externalTaskIds) {
      topics.add(
          taktClient.requestExternalTaskTopic(externalTaskId, 3, CleanupPolicy.COMPACT, (short) 1));
    }
    Awaitility.await()
        .atMost(DEFAULT_DURATION)
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              log.info(
                  "Waiting for external task topics {} to be available in cache {}",
                  topics,
                  topicMetaCache);
              return topicMetaCache.keySet().containsAll(topics);
            });
  }

  public BpmnTestEngine startProcessInstance(VariablesDTO variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);

    UUID newProcessInstanceId =
        taktClient.startProcess(processDefinitionKey.getProcessDefinitionId(), variables);

    log.info("Starting process instanceToContinue {}", newProcessInstanceId);
    Awaitility.await()
        .atMost(DEFAULT_DURATION)
        .until(
            () -> {
              log.info(
                  "Checking processInstanceMap {}", System.identityHashCode(processInstanceMap));
              return processInstanceMap.containsKey(newProcessInstanceId);
            });
    activeProcessInstanceId = newProcessInstanceId;

    return this;
  }

  public BpmnTestEngine waitForExternalTaskTrigger(String taskId) {
    return waitForExternalTaskTrigger(taskId, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilUserTaskIsWaitingForResponse(String elementId) {
    return waitUntilUserTaskIsWaitingForResponse(elementId, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilUserTaskIsWaitingForResponse(String elementId, Duration duration) {
    log.info(
        "waitUntilUserTaskIsWaitingForResponse {} {} {}",
        activeProcessInstanceId,
        userTaskTriggerQueueMap,
        System.identityHashCode(userTaskTriggerQueueMap));
    activeUserTaskTrigger =
        Awaitility.await()
            .atMost(duration)
            .until(
                this::pollUserTask,
                userTaskTrigger ->
                    userTaskTrigger != null
                        && userTaskTrigger.getProcessInstanceId().equals(activeProcessInstanceId)
                        && getScopeWithElementId(activeProcessInstanceId, elementId).stream()
                            .anyMatch(FlowNodeInstanceDTO::isActive));

    return this;
  }

  public BpmnTestEngine waitForExternalTaskTrigger(String taskId, Duration duration) {
    log.info(
        "waitForExternalTaskTrigger {} {}", activeProcessInstanceId, activeExternalTaskTriggers);
    Awaitility.await()
        .atMost(duration)
        .until(
            () ->
                activeExternalTaskTriggers
                    .getOrDefault(activeProcessInstanceId, Map.of())
                    .get(taskId),
            Objects::nonNull);

    return this;
  }

  public BpmnTestEngine waitForNewProcessInstance() {
    return waitForNewProcessInstance(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitForNewProcessInstance(Duration duration) {
    UUID referenceProcessInstanceId = latestInstantiatedProcessInstanceId;
    activeProcessInstanceId =
        Awaitility.await()
            .atMost(duration)
            .until(
                () -> latestInstantiatedProcessInstanceId,
                instance -> !Objects.equals(referenceProcessInstanceId, instance));
    return this;
  }

  public BpmnTestEngine waitUntilChildProcessIsCompleted(String childProcessName) {
    return waitUntilChildProcessesHaveState(childProcessName, ExecutionState.COMPLETED);
  }

  public BpmnTestEngine waitUntilChildProcessIsStarted(String childProcessName) {
    return waitUntilChildProcessesHaveState(childProcessName, ExecutionState.ACTIVE);
  }

  public BpmnTestEngine waitUntilChildProcessIsTerminated(String childProcessName) {
    return waitUntilChildProcessesHaveState(childProcessName, ExecutionState.ABORTED);
  }

  public BpmnTestEngine waitUntilChildProcessesHaveState(
      String childProcessName, ExecutionState processInstanceState) {
    activeProcessInstanceId =
        Awaitility.await()
            .atMost(DEFAULT_DURATION)
            .until(
                () -> {
                  Set<UUID> childKeys = processInstanceParentChildMap.get(activeProcessInstanceId);
                  if (childKeys == null) {
                    return null;
                  }
                  return processInstanceMap.values().stream()
                      .filter(
                          pi ->
                              pi.getProcessDefinitionKey()
                                      .getProcessDefinitionId()
                                      .equals(childProcessName)
                                  && pi.getScope().getState() == processInstanceState)
                      .map(ProcessInstanceDTO::getProcessInstanceId)
                      .findFirst()
                      .orElse(null);
                },
                Objects::nonNull);
    return this;
  }

  public BpmnTestEngine andRespondToExternalTaskWithSuccess(
      ExternalTaskTriggerDTO externalTaskTrigger, VariablesDTO variables) {
    taktClient.respondToExternalTask(externalTaskTrigger).respondSuccess(variables.getVariables());
    return this;
  }

  public BpmnTestEngine andRespondToExternalTaskWithSuccess(String taskId, VariablesDTO variables) {
    ExternalTaskTriggerDTO externalTaskTriggerDTO =
        activeExternalTaskTriggers.getOrDefault(activeProcessInstanceId, Map.of()).get(taskId);
    taktClient
        .respondToExternalTask(externalTaskTriggerDTO)
        .respondSuccess(variables.getVariables());
    return this;
  }

  public BpmnTestEngine andCompleteUserTaskWithSuccess(VariablesDTO variables) {
    taktClient.completeUserTask(activeUserTaskTrigger).respondSuccess(variables.getVariables());
    return this;
  }

  public BpmnTestEngine andCompleteUserTaskWithError(
      String code, String message, VariablesDTO variables) {
    taktClient.completeUserTask(activeUserTaskTrigger).respondError(code, message, variables);
    return this;
  }

  public BpmnTestEngine andCompleteUserTaskWithEscalation(
      String code, String message, VariablesDTO variables) {
    taktClient.completeUserTask(activeUserTaskTrigger).respondEscalation(code, message, variables);
    return this;
  }

  public BpmnTestEngine andRespondToExternalTaskWithPromise(String taskId, String newTimeout) {
    ExternalTaskTriggerDTO externalTaskTriggerDTO =
        activeExternalTaskTriggers.getOrDefault(activeProcessInstanceId, Map.of()).get(taskId);
    taktClient
        .respondToExternalTask(externalTaskTriggerDTO)
        .respondPromise(Duration.parse(newTimeout));
    return this;
  }

  public BpmnTestEngine andRespondToExternalTaskWithError(
      String taskId, boolean allowRetry, String code, String message, VariablesDTO variables) {
    ExternalTaskTriggerDTO externalTaskTriggerDTO =
        activeExternalTaskTriggers.getOrDefault(activeProcessInstanceId, Map.of()).get(taskId);
    taktClient
        .respondToExternalTask(externalTaskTriggerDTO)
        .respondError(allowRetry, code, message, variables);
    return this;
  }

  public BpmnTestEngine andRespondToExternalTaskWithError(
      String taskId, boolean allowRetry, String code, String message) {
    return andRespondToExternalTaskWithError(
        taskId, allowRetry, code, message, VariablesDTO.empty());
  }

  public BpmnTestEngine andRespondToExternalTaskWithEscalation(
      String taskId, String code, String message, VariablesDTO variables) {
    ExternalTaskTriggerDTO externalTaskTriggerDTO =
        activeExternalTaskTriggers.getOrDefault(activeProcessInstanceId, Map.of()).get(taskId);
    taktClient
        .respondToExternalTask(externalTaskTriggerDTO)
        .respondEscalation(code, message, variables);
    return this;
  }

  public BpmnTestEngine waitUntilDone() {
    return waitUntilDone(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilIncident() {
    return waitUntilIncident(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilDone(Duration duration) {
    Awaitility.await()
        .atMost(duration)
        .until(
            () -> {
              if (activeProcessInstanceId != null
                  && processInstanceMap
                      .get(activeProcessInstanceId)
                      .getScope()
                      .getState()
                      .isDone()) {
                return activeProcessInstanceId;
              }
              return null;
            },
            Objects::nonNull);
    return this;
  }

  public BpmnTestEngine waitUntilIncident(Duration duration) {
    Awaitility.await()
        .atMost(duration)
        .until(
            () -> {
              if (activeProcessInstanceId != null
                  && processInstanceMap.get(activeProcessInstanceId).getIncidentInfo() != null) {
                return activeProcessInstanceId;
              }
              return null;
            },
            Objects::nonNull);
    return this;
  }

  public ProcessInstanceAssert assertThatProcess() {
    return new ProcessInstanceAssert(activeProcessInstanceId, this);
  }

  public UserTaskAssert assertThatUserTask() {
    return new UserTaskAssert(activeUserTaskTrigger, this);
  }

  public BpmnTestEngine parentProcess() {
    ProcessInstanceDTO processInstanceDTO = processInstanceMap.get(activeProcessInstanceId);

    activeProcessInstanceId = processInstanceDTO.getParentProcessInstanceId();
    return this;
  }

  public ProcessInstanceAssert assertThatParentProcess() {
    ProcessInstanceDTO processInstanceDTO = processInstanceMap.get(activeProcessInstanceId);
    ProcessInstanceDTO parentProcessInstance =
        processInstanceMap.get(processInstanceDTO.getParentProcessInstanceId());
    activeProcessInstanceId = parentProcessInstance.getProcessInstanceId();
    return new ProcessInstanceAssert(parentProcessInstance.getProcessInstanceId(), this);
  }

  public ProcessDefinitionDTO deployedProcessDefinition() {
    return activeProcessDefintion;
  }

  public BpmnTestEngine waitFor(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
    return this;
  }

  public BpmnTestEngine moveTimeForward(Duration duration) {
    mutableClock.advanceBy(duration);
    Instant now = Instant.now(mutableClock);
    log.info("Advanced the time by {} to {} {}", duration, now, now.toEpochMilli());
    return this;
  }

  public BpmnTestEngine setTime(Instant newInstant) {
    log.info("Setting the time to: {}", newInstant);
    mutableClock.set(newInstant);
    return this;
  }

  public BpmnTestEngine abortProcessInstance() {
    taktClient.abortElementInstance(activeProcessInstanceId);
    return this;
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId, int count) {
    return waitUntilElementHasPassed(elementId, count, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId, int count, Duration duration) {
    Awaitility.await()
        .atMost(duration)
        .until(
            () -> getScopeWithElementId(activeProcessInstanceId, elementId),
            instances -> !instances.isEmpty() && instances.getFirst().getPassedCnt() == count);
    return this;
  }

  public List<FlowNodeInstanceDTO> getScopeWithElementId(
      UUID processInstanceId, String elementPath) {

    String[] split = elementPath.split(":");
    String processDefinitionId;
    if (split.length == 2) {
      processDefinitionId = split[0];
      elementPath = split[1];
    } else {
      processDefinitionId =
          activeProcessDefintion.getDefinitions().getDefinitionsKey().getProcessDefinitionId();
      elementPath = split[0];
    }
    List<String> elementPathList = Stream.of(elementPath.split("/")).toList();
    Map<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> filteredByProcessInstance =
        flowNodeInstanceMap.entrySet().stream()
            .filter(e -> e.getKey().getProcessInstanceId().equals(processInstanceId))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    Map<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> filteredByElementIdOnIndex =
        filteredByProcessInstance;

    List<String> elementIdIndex = elementIdIndexMap.get(processDefinitionId);
    for (int index = 0; index < elementPathList.size(); index++) {
      int currentIndex = index;
      filteredByElementIdOnIndex =
          filteredByProcessInstance.entrySet().stream()
              .filter(
                  e ->
                      e.getValue().getElementIndex()
                          == (elementIdIndex.indexOf(elementPathList.get(currentIndex))))
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    return new ArrayList<>(filteredByElementIdOnIndex.values());
  }

  public List<FlowNodeInstanceDTO> getScopePathToElementId(
      UUID processInstanceId, String elementPath) {

    List<String> elementPathList = Stream.of(elementPath.split("/")).toList();
    Map<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> filteredByProcessInstance =
        flowNodeInstanceMap.entrySet().stream()
            .filter(e -> e.getKey().getProcessInstanceId().equals(processInstanceId))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    List<FlowNodeInstanceDTO> path = new ArrayList<>();
    for (int index = 0; index < elementPathList.size(); index++) {
      int currentIndex = index;

      Optional<FlowNodeInstanceDTO> first =
          filteredByProcessInstance.values().stream()
              .filter(
                  flowNodeInstanceDTO ->
                      flowNodeInstanceDTO.getElementId().equals(elementPathList.get(currentIndex)))
              .findFirst();
      if (first.isPresent()) {
        path.add(first.get());
      } else {
        throw new IllegalStateException(
            "No flow node instance found for element id: " + elementPathList.get(currentIndex));
      }
    }

    return path;
  }

  public BpmnTestEngine sendMessage(String messageName, VariablesDTO variables) {
    LOG.info("Sending message: " + messageName);
    DefinitionMessageEventTriggerDTO messageEvent =
        new DefinitionMessageEventTriggerDTO(messageName, variables);
    taktClient.sendMessage(messageEvent);
    return this;
  }

  public BpmnTestEngine waitForMessageSubscription(String messageName) {
    return waitForMessageSubscription(messageName, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitForMessageSubscription(String messageName, Duration duration) {
    Awaitility.await()
        .atMost(duration)
        .until(() -> messageSubscriptionMap.get(messageName), Objects::nonNull);
    return this;
  }

  public BpmnTestEngine waitForMessageSubscription(
      String receiveTaskMessage, Set<String> correlationKeys) {
    return waitForMessageSubscription(receiveTaskMessage, correlationKeys, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitForMessageSubscription(
      String messageName, Set<String> correlationKeys, Duration duration) {
    Set<String> remainingCorrelationKeys = new HashSet<>(correlationKeys);
    Awaitility.await()
        .atMost(duration)
        .until(
            () -> {
              ConcurrentLinkedQueue<MessageEventDTO> messageEvents =
                  messageSubscriptionMap.get(messageName);
              if (messageEvents == null) {
                return false;
              }
              MessageEventDTO poll;
              do {
                poll = messageEvents.poll();
                if (poll
                    instanceof CorrelationMessageSubscriptionDTO correlationMessageSubscription) {
                  remainingCorrelationKeys.remove(
                      correlationMessageSubscription.getCorrelationKey());
                  return remainingCorrelationKeys.isEmpty();
                }
              } while (poll != null);

              return false;
            },
            found -> found);
    return this;
  }

  public BpmnTestEngine andSendMessageWithCorrelationKey(
      String messageName, String correlationKey, VariablesDTO variables) {
    LOG.info("Sending message: " + messageName);
    CorrelationMessageEventTriggerDTO messageEvent =
        new CorrelationMessageEventTriggerDTO(messageName, correlationKey, variables);
    taktClient.sendMessage(messageEvent);
    return this;
  }

  public ProcessInstanceDTO getProcessInstance(UUID parentInstanceKey) {
    return processInstanceMap.get(parentInstanceKey);
  }

  public VariablesDTO getVariables(UUID processInstanceId) {
    return variablesMap.get(processInstanceId);
  }

  public BpmnTestEngine waitUntilActivityHasState(String elementId, ExecutionState state) {
    return waitUntilActivityHasState(elementId, state, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilIdle() {
    return waitUntilIdle(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilClientIdle() {
    return waitUntilClientIdle(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilIdle(Duration duration) {
    return waitUntilIdle(
        duration,
        adminClientHelper.listConsumerGroups().stream()
            .filter(this::isEngineConsumerGroup)
            .collect(Collectors.toSet()));
  }

  public BpmnTestEngine waitUntilClientIdle(Duration duration) {
    return waitUntilIdle(
        duration,
        adminClientHelper.listConsumerGroups().stream()
            .filter(consumerGroup -> !isEngineConsumerGroup(consumerGroup))
            .collect(Collectors.toSet()));
  }

  public BpmnTestEngine waitUntilIdle(Duration duration, Set<String> consumerGroups) {
    // Scan all topics from Topics class and wait until all consumer lags are 0
    Awaitility.await()
        .atMost(duration)
        .until(
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                boolean allLagsZero = true;
                for (String consumerGroup : consumerGroups) {
                  allLagsZero &= allLagsZeroForGroupId(consumerGroup);
                }
                return allLagsZero;
              }

              private boolean allLagsZeroForGroupId(String consumerGroup) {
                List<Long> allConsumerLags =
                    adminClientHelper.getAllConsumerLags(consumerGroup).values().stream()
                        .map(ConsumerLagInfo::getTotalLag)
                        .filter(lag -> lag > 0)
                        .toList();
                if (!allConsumerLags.isEmpty()) {
                  log.info(
                      "lags not empty for consumerGroup {} {}", consumerGroup, allConsumerLags);
                }
                return allConsumerLags.isEmpty();
              }
            });
    return this;
  }

  private boolean isEngineConsumerGroup(String consumerGroup) {
    return consumerGroup.startsWith("xml-by-process-definition-id-consumer")
        || consumerGroup.equals("taktx-engine")
        || consumerGroup.equals("taktx-topicmanager-request-consumer")
        || consumerGroup.startsWith("taktx-topicmanager-actuel-consumer");
  }

  public BpmnTestEngine waitUntilActivityHasState(
      String elementId, ExecutionState state, Duration duration) {
    selectedFlowNodeInstance =
        Awaitility.await()
            .atMost(duration)
            .until(
                () -> {
                  List<FlowNodeInstanceDTO> flowNodeInstanceWithElementId =
                      getScopeWithElementId(activeProcessInstanceId, elementId);
                  return flowNodeInstanceWithElementId.getFirst()
                              instanceof ActivityInstanceDTO activityInstance
                          && activityInstance.getState() == state
                      ? activityInstance
                      : null;
                },
                Objects::nonNull);

    return this;
  }

  public BpmnTestEngine waitUntilElementIsActive(String elementId, Duration duration) {
    return waitUntilActivityHasState(elementId, ExecutionState.ACTIVE, duration);
  }

  public BpmnTestEngine waitUntilElementIsActive(String elementId) {
    return waitUntilElementIsActive(elementId, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasAborted(String elementId) {
    return waitUntilActivityHasState(elementId, ExecutionState.ABORTED, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementIsWaiting(String elementId) {
    return waitUntilActivityHasState(elementId, ExecutionState.ACTIVE, DEFAULT_DURATION);
  }

  public BpmnTestEngine terminateElementInstance() {
    taktClient.abortElementInstance(
        activeProcessInstanceId, List.of(selectedFlowNodeInstance.getElementInstanceId()));
    return this;
  }

  public void reset() {
    this.processInstanceParentChildMap.clear();
    this.processInstanceMap.clear();
    this.flowNodeInstanceMap.clear();
    this.mutableClock.set(originalClock.instant());
  }

  /** Waits for Kafka broker and required topics to be available. */
  private void waitForKafkaTopics() {
    LOG.info("Waiting for Kafka broker and required topics to be available...");

    final Set<String> requiredTopics =
        Set.of(
            TOPIC_TEST_PREFIX + Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
            TOPIC_TEST_PREFIX + Topics.MESSAGE_EVENT_TOPIC.getTopicName(),
            TOPIC_TEST_PREFIX + Topics.TOPIC_META_ACTUAL_TOPIC.getTopicName());

    Awaitility.await()
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              boolean allTopicsAvailable = topicMetaCache.keySet().containsAll(requiredTopics);
              if (!allTopicsAvailable) {
                LOG.info("Still waiting for topics: " + topicMetaCache.keySet());
              }
              return allTopicsAvailable;
            });

    LOG.info("Kafka broker and all required topics are now available");
  }

  public BpmnTestEngine waitForSignalSubscription(String name) {
    Awaitility.await()
        .atMost(DEFAULT_DURATION)
        .until(
            () -> {
              ConcurrentLinkedQueue<SignalDTO> signalDTOS = signalMap.get(name);
              if (signalDTOS != null && !signalDTOS.isEmpty()) {
                return signalDTOS.stream()
                    .anyMatch(NewInstanceSignalSubscriptionDTO.class::isInstance);
              }
              return false;
            });
    return this;
  }

  public BpmnTestEngine waitForSignal(String name) {
    Awaitility.await()
        .atMost(DEFAULT_DURATION)
        .until(
            () -> {
              ConcurrentLinkedQueue<SignalDTO> signalDTOS = signalMap.get(name);
              if (signalDTOS != null && !signalDTOS.isEmpty()) {
                return signalDTOS.stream()
                    .anyMatch(signalDTO -> signalDTO.getClass() == SignalDTO.class);
              }
              return false;
            });
    return this;
  }

  public BpmnTestEngine sendSignal(String signalName) {
    taktClient.sendSignal(signalName);
    return this;
  }

  public BpmnTestEngine assertThatExternalTaskTriggerHasTaskHeader(
      String taskId, String header, String headerValue) {
    ExternalTaskTriggerDTO externalTaskTriggerDTO =
        activeExternalTaskTriggers.getOrDefault(activeProcessInstanceId, Map.of()).get(taskId);
    assertThat(externalTaskTriggerDTO).isNotNull();
    assertThat(externalTaskTriggerDTO.getHeaders()).containsEntry(header, headerValue);
    return this;
  }

  public BpmnTestEngine setVariablesAtRootScope(VariablesDTO vars) {
    taktClient.setVariable(activeProcessInstanceId, List.of(), vars);
    return this;
  }

  public BpmnTestEngine setVariablesForElement(String elementIdPath, VariablesDTO vars) {
    List<FlowNodeInstanceDTO> scopeWithElementId =
        getScopePathToElementId(activeProcessInstanceId, elementIdPath);
    List<Long> idList =
        scopeWithElementId.stream().map(FlowNodeInstanceDTO::getElementInstanceId).toList();
    taktClient.setVariable(activeProcessInstanceId, idList, vars);
    return this;
  }
}
