/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.testengine;

import io.taktx.CleanupPolicy;
import io.taktx.Topics;
import io.taktx.client.ExternalTaskTriggerConsumer;
import io.taktx.client.TaktClient;
import io.taktx.client.UserTaskTriggerConsumer;
import io.taktx.client.serdes.TopicMetaJsonDeserializer;
import io.taktx.dto.ActivityInstanceDTO;
import io.taktx.dto.ActtivityStateEnum;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.FlowElementsDTO;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ProcessInstanceState;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.SubProcessDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.generic.MutableClock;
import io.taktx.engine.generic.TopologyProducer;
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
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;

public class BpmnTestEngine {
  private static final Logger LOG = Logger.getLogger(BpmnTestEngine.class);
  private static final Duration DEFAULT_DURATION = Duration.ofSeconds(10);
  private static final String TOPIC_TEST_PREFIX = "tenant.namespace.";
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(BpmnTestEngine.class);

  private TaktClient taktClient;

  private final Map<UUID, Set<UUID>> processInstanceParentChildMap = new ConcurrentHashMap<>();
  private final Map<UUID, ConcurrentLinkedQueue<ExternalTaskTriggerDTO>>
      externalTaskTriggerQueueMap = new ConcurrentHashMap<>();
  private final Map<UUID, ConcurrentLinkedQueue<UserTaskTriggerDTO>> userTaskTriggerQueueMap =
      new ConcurrentHashMap<>();
  private final Map<UUID, ProcessInstanceDTO> processInstanceMap = new ConcurrentHashMap<>();
  private final Map<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceMap =
      new ConcurrentHashMap<>();
  private final Map<UUID, VariablesDTO> variablesMap = new ConcurrentHashMap<>();
  private final Map<String, ConcurrentLinkedQueue<MessageEventDTO>> messageSubscriptionMap =
      new ConcurrentHashMap<>();
  private ProcessDefinitionDTO activeProcessDefintion;
  private UUID activeProcessInstanceKey;
  private ExternalTaskTriggerDTO activeExternalTaskTrigger;
  private UserTaskTriggerDTO activeUserTaskTrigger;
  private ParsedDefinitionsDTO definitionsBeingDeployed;
  private final Clock originalClock;
  private final MutableClock mutableClock;
  private UUID latestInstantiatedProcessInstanceKey;
  private KafkaConsumerUtil<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerConsumer;
  private KafkaConsumerUtil<MessageEventKeyDTO, MessageEventDTO> messageEventConsumer;
  private KafkaConsumerUtil<String, TopicMetaDTO> actualTopicMetaConsumer;
  private FlowNodeInstanceDTO selectedFlowNodeInstance;
  private final Map<String, List<String>> elementIdIndexMap = new HashMap<>();
  private Map<String, TopicMetaDTO> topitMetaCache = new ConcurrentHashMap<>();

  public BpmnTestEngine(Clock clock) {
    this.originalClock = Clock.fixed(clock.instant(), clock.getZone());
    this.mutableClock = (MutableClock) clock;
  }

  private static @NotNull ProcessInstanceDTO getProcessInstanceDTO(
      UUID processInstanceKey, ProcessInstanceUpdateDTO processInstanceUpdate) {
    return new ProcessInstanceDTO(
        processInstanceKey,
        processInstanceUpdate.getParentProcessInstanceKey(),
        processInstanceUpdate.getFlowNodeInstances(),
        processInstanceUpdate.getParentElementInstancePath(),
        processInstanceUpdate.getProcessDefinitionKey(),
        false,
        Set.of());
  }

  public void close() {
    LOG.info("Closing bpmn test engine");
    taktClient.stop();
    processInstanceTriggerConsumer.stop();
    messageEventConsumer.stop();
    actualTopicMetaConsumer.stop();
  }

  public void init() {
    String kafkaBootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);

    Properties kakaProperties = new Properties();
    kakaProperties.put("bootstrap.servers", kafkaBootstrapServers);

    taktClient =
        TaktClient.newClientBuilder()
            .withTenant("tenant")
            .withNamespace("namespace")
            .withKafkaProperties(kakaProperties)
            .build();
    taktClient.registerInstanceUpdateConsumer(BpmnTestEngine.this::consume);
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
        "Received process instanceToContinue trigger: "
            + trigger
            + " "
            + trigger.getClass().getName());
    if (trigger instanceof StartCommandDTO startCommand
        && startCommand.getParentProcessInstanceKey() != null) {
      Set<UUID> uuids1 =
          processInstanceParentChildMap.computeIfAbsent(
              startCommand.getParentProcessInstanceKey(), k -> new ConcurrentSkipListSet<>());
      uuids1.add(startCommand.getProcessInstanceKey());
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

  public void consumeTopicMeta(
      ConsumerRecord<String, TopicMetaDTO> topicMetaRecord) {
    TopicMetaDTO topicMetaDTO = topicMetaRecord.value();
    topitMetaCache.put(topicMetaDTO.getTopicName(), topicMetaDTO);
    LOG.info("Received topic meta event: {}" + topicMetaDTO);
  }

  public void consumeExternalTaskTrigger(ExternalTaskTriggerDTO externalTaskTrigger) {

    LOG.info("Received external task trigger: " + externalTaskTrigger);

    ConcurrentLinkedQueue<ExternalTaskTriggerDTO> externalTaskTriggers =
        externalTaskTriggerQueueMap.computeIfAbsent(
            externalTaskTrigger.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    externalTaskTriggers.add(externalTaskTrigger);
    LOG.info(
        "External task triggers: "
            + externalTaskTriggerQueueMap
            + " "
            + System.identityHashCode(externalTaskTriggerQueueMap));
  }

  public void consumeUserTaskTrigger(UserTaskTriggerDTO userTaskTrigger) {

    LOG.info("Received user task trigger: " + userTaskTrigger);

    ConcurrentLinkedQueue<UserTaskTriggerDTO> uerTaskTriggers =
        userTaskTriggerQueueMap.computeIfAbsent(
            userTaskTrigger.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    uerTaskTriggers.add(userTaskTrigger);
    LOG.info(
        "User task triggers: "
            + userTaskTriggerQueueMap
            + " "
            + System.identityHashCode(userTaskTriggerQueueMap));
  }

  public void consume(UUID processInstanceKey, InstanceUpdateDTO instanceUpdate) {
    if (instanceUpdate instanceof ProcessInstanceUpdateDTO processInstanceUpdate) {
      LOG.info(
          "Received process instanceToContinue update: "
              + processInstanceKey
              + " "
              + instanceUpdate);

      ProcessInstanceDTO processInstanceDTO =
          getProcessInstanceDTO(processInstanceKey, processInstanceUpdate);
      log.info(
          "Adding to process instanceToContinue map {} {}",
          System.identityHashCode(processInstanceMap),
          processInstanceDTO);
      ProcessInstanceDTO previousProcessInstance =
          processInstanceMap.put(processInstanceKey, processInstanceDTO);
      if (previousProcessInstance == null) {
        latestInstantiatedProcessInstanceKey = processInstanceDTO.getProcessInstanceKey();
      }

      VariablesDTO existingVariables =
          variablesMap.computeIfAbsent(processInstanceKey, k -> VariablesDTO.empty());
      existingVariables.getVariables().putAll(processInstanceUpdate.getVariables().getVariables());

    } else if (instanceUpdate instanceof FlowNodeInstanceUpdateDTO flowNodeInstanceUpdate) {
      LOG.info("Received FlowNode instanceToContinue update: " + instanceUpdate);

      FlowNodeInstanceKeyDTO key =
          new FlowNodeInstanceKeyDTO(
              processInstanceKey, flowNodeInstanceUpdate.getFlowNodeInstancePath());
      this.flowNodeInstanceMap.put(key, flowNodeInstanceUpdate.getFlowNodeInstance());

      VariablesDTO existingVariables =
          variablesMap.computeIfAbsent(key.getProcessInstanceKey(), k -> VariablesDTO.empty());
      existingVariables.getVariables().putAll(flowNodeInstanceUpdate.getVariables().getVariables());
    }
  }

  public UserTaskTriggerDTO pollUserTask() {
    ConcurrentLinkedQueue<UserTaskTriggerDTO> externalTaskTriggers =
        userTaskTriggerQueueMap.get(activeProcessInstanceKey);
    log.info("polled user task queue {} {}", activeProcessInstanceKey, externalTaskTriggers);
    if (externalTaskTriggers == null) {
      return null;
    }
    UserTaskTriggerDTO poll = externalTaskTriggers.poll();
    log.info("polled user task queue {} {}", externalTaskTriggers, poll);
    return poll;
  }

  public ExternalTaskTriggerDTO pollExternalTask() {
    ConcurrentLinkedQueue<ExternalTaskTriggerDTO> externalTaskTriggers =
        externalTaskTriggerQueueMap.get(activeProcessInstanceKey);
    log.info("polled external task queue {} {}", activeProcessInstanceKey, externalTaskTriggers);
    if (externalTaskTriggers == null) {
      return null;
    }
    ExternalTaskTriggerDTO poll = externalTaskTriggers.poll();
    log.info("polled external task queue {} {}", externalTaskTriggers, poll);
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
            public void accept(ExternalTaskTriggerDTO value) {
              BpmnTestEngine.this.consumeExternalTaskTrigger(value);
            }
          });
    }
  }

  private void registerTopicsForExternalTasks(String... externalTaskIds) {
    List<String> topics = new ArrayList<>();

    for (String externalTaskId : externalTaskIds) {
      topics.add(taktClient.requestExternalTaskTopic(
          externalTaskId, 3, CleanupPolicy.COMPACT));
    }
    Awaitility.await()
        .atMost(DEFAULT_DURATION)
        .until(() ->
            topitMetaCache.keySet().containsAll(topics)
        );

  }

  public BpmnTestEngine startProcessInstance(VariablesDTO variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);

    UUID newProcessInstanceKey =
        taktClient.startProcess(processDefinitionKey.getProcessDefinitionId(), variables);

    log.info("Starting process instanceToContinue {}", newProcessInstanceKey);
    Awaitility.await()
        .atMost(DEFAULT_DURATION)
        .until(
            () -> {
              log.info(
                  "Checking processInstanceMap {}", System.identityHashCode(processInstanceMap));
              return processInstanceMap.containsKey(newProcessInstanceKey);
            });
    activeProcessInstanceKey = newProcessInstanceKey;

    return this;
  }

  public BpmnTestEngine waitUntilExternalTaskIsWaitingForResponse(
      Map<String, BiConsumer<BpmnTestEngine, ExternalTaskTriggerDTO>> externalTaskTriggerQueueMap) {
    Set<String> externalTaskIds = new HashSet<>(externalTaskTriggerQueueMap.keySet());
    Awaitility.await()
        .atMost(DEFAULT_DURATION)
        .until(
            this::pollExternalTask,
            externalTaskTrigger -> {
              String elementIdPath = "";
              List<Long> triggerElementInstanceIdPath =
                  externalTaskTrigger.getElementInstanceIdPath();
              List<Long> elementInstanceIdPath = new ArrayList<>();
              for (int i = 0; i < triggerElementInstanceIdPath.size(); i++) {
                if (i > 0) {
                  elementIdPath += "/";
                }
                elementInstanceIdPath.add(triggerElementInstanceIdPath.get(i));

                FlowNodeInstanceKeyDTO flowNodeInstanceKeyDTO =
                    new FlowNodeInstanceKeyDTO(activeProcessInstanceKey, elementInstanceIdPath);
                FlowNodeInstanceDTO flowNodeInstanceDTO =
                    flowNodeInstanceMap.get(flowNodeInstanceKeyDTO);
                elementIdPath += flowNodeInstanceDTO.getElementId();
              }

              BiConsumer<BpmnTestEngine, ExternalTaskTriggerDTO> bpmnTestEngineConsumer =
                  externalTaskTriggerQueueMap.get(elementIdPath);
              if (bpmnTestEngineConsumer != null) {
                bpmnTestEngineConsumer.accept(this, externalTaskTrigger);
                externalTaskIds.remove(elementIdPath);
              }
              return externalTaskIds.isEmpty();
            });
    return this;
  }

  public BpmnTestEngine waitUntilExternalTaskIsWaitingForResponse(String elementId) {
    return waitUntilExternalTaskIsWaitingForResponse(elementId, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilUserTaskIsWaitingForResponse(String elementId) {
    return waitUntilUserTaskIsWaitingForResponse(elementId, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilUserTaskIsWaitingForResponse(String elementId, Duration duration) {
    log.info(
        "waitUntilUserTaskIsWaitingForResponse {} {} {}",
        activeProcessInstanceKey,
        externalTaskTriggerQueueMap,
        System.identityHashCode(externalTaskTriggerQueueMap));
    activeUserTaskTrigger =
        Awaitility.await()
            .atMost(duration)
            .until(
                this::pollUserTask,
                userTaskTrigger ->
                    userTaskTrigger != null
                        && userTaskTrigger.getProcessInstanceKey().equals(activeProcessInstanceKey)
                        && getFlowNodeInstancesWithElementId(activeProcessInstanceKey, elementId)
                            .stream()
                            .anyMatch(FlowNodeInstanceDTO::isWaiting));

    return this;
  }

  public BpmnTestEngine waitUntilExternalTaskIsWaitingForResponse(
      String elementId, Duration duration) {
    log.info(
        "waitUntilExternalTaskIsWaitingForResponse {} {} {}",
        activeProcessInstanceKey,
        externalTaskTriggerQueueMap,
        System.identityHashCode(externalTaskTriggerQueueMap));
    activeExternalTaskTrigger =
        Awaitility.await()
            .atMost(duration)
            .until(
                this::pollExternalTask,
                externalTaskTrigger ->
                    externalTaskTrigger != null
                        && externalTaskTrigger
                            .getProcessInstanceKey()
                            .equals(activeProcessInstanceKey)
                        && getFlowNodeInstancesWithElementId(activeProcessInstanceKey, elementId)
                            .stream()
                            .allMatch(FlowNodeInstanceDTO::isWaiting));

    return this;
  }

  public BpmnTestEngine waitForNewProcessInstance() {
    return waitForNewProcessInstance(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitForNewProcessInstance(Duration duration) {
    UUID referenceProcessInstanceKey = latestInstantiatedProcessInstanceKey;
    activeProcessInstanceKey =
        Awaitility.await()
            .atMost(duration)
            .until(
                () -> latestInstantiatedProcessInstanceKey,
                instance -> !Objects.equals(referenceProcessInstanceKey, instance));
    return this;
  }

  public BpmnTestEngine waitUntilChildProcessIsCompleted(String childProcessName) {
    return waitUntilChildProcessesHaveState(childProcessName, ProcessInstanceState.COMPLETED);
  }

  public BpmnTestEngine waitUntilChildProcessIsStarted(String childProcessName) {
    return waitUntilChildProcessesHaveState(childProcessName, ProcessInstanceState.ACTIVE);
  }

  public BpmnTestEngine waitUntilChildProcessIsTerminated(String childProcessName) {
    return waitUntilChildProcessesHaveState(childProcessName, ProcessInstanceState.TERMINATED);
  }

  public BpmnTestEngine waitUntilChildProcessesHaveState(
      String childProcessName, ProcessInstanceState processInstanceState) {
    activeProcessInstanceKey =
        Awaitility.await()
            .atMost(DEFAULT_DURATION)
            .until(
                () -> {
                  Set<UUID> childKeys = processInstanceParentChildMap.get(activeProcessInstanceKey);
                  if (childKeys == null) {
                    return null;
                  }
                  return processInstanceMap.values().stream()
                      .filter(
                          pi ->
                              pi.getProcessDefinitionKey()
                                      .getProcessDefinitionId()
                                      .equals(childProcessName)
                                  && pi.getFlowNodeInstances().getState() == processInstanceState)
                      .map(ProcessInstanceDTO::getProcessInstanceKey)
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

  public BpmnTestEngine andRespondToExternalTaskWithSuccess(VariablesDTO variables) {
    taktClient
        .respondToExternalTask(activeExternalTaskTrigger)
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

  public BpmnTestEngine andRespondToExternalTaskWithPromise(String newTimeout) {
    taktClient
        .respondToExternalTask(activeExternalTaskTrigger)
        .respondPromise(Duration.parse(newTimeout));
    return this;
  }

  public BpmnTestEngine andRespondToExternalTaskWithFailure(
      boolean allowRetry, String code, String message, VariablesDTO variables) {
    taktClient
        .respondToExternalTask(activeExternalTaskTrigger)
        .respondError(allowRetry, code, message, variables);
    return this;
  }

  public BpmnTestEngine andRespondToExternalTaskWithEscalation(
      String code, String message, VariablesDTO variables) {
    taktClient
        .respondToExternalTask(activeExternalTaskTrigger)
        .respondEscalation(code, message, variables);
    return this;
  }

  public BpmnTestEngine waitUntilCompleted() {
    return waitUntilCompleted(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilCompleted(Duration duration) {
    Awaitility.await()
        .atMost(duration)
        .until(
            () -> {
              if (activeProcessInstanceKey != null
                  && processInstanceMap
                      .get(activeProcessInstanceKey)
                      .getFlowNodeInstances()
                      .getState()
                      .isFinished()) {
                return activeProcessInstanceKey;
              }
              return null;
            },
            Objects::nonNull);
    return this;
  }

  public ProcessInstanceAssert assertThatProcess() {
    return new ProcessInstanceAssert(activeProcessInstanceKey, this);
  }

  public UserTaskAssert assertThatUserTask() {
    return new UserTaskAssert(activeUserTaskTrigger, this);
  }

  public BpmnTestEngine parentProcess() {
    ProcessInstanceDTO processInstanceDTO = processInstanceMap.get(activeProcessInstanceKey);

    activeProcessInstanceKey = processInstanceDTO.getParentProcessInstanceKey();
    return this;
  }

  public ProcessInstanceAssert assertThatParentProcess() {
    ProcessInstanceDTO processInstanceDTO = processInstanceMap.get(activeProcessInstanceKey);
    ProcessInstanceDTO parentProcessInstance =
        processInstanceMap.get(processInstanceDTO.getParentProcessInstanceKey());
    activeProcessInstanceKey = parentProcessInstance.getProcessInstanceKey();
    return new ProcessInstanceAssert(parentProcessInstance.getProcessInstanceKey(), this);
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

  public BpmnTestEngine doNotExpectNewProcessInstance() {
    Assertions.assertThrows(ConditionTimeoutException.class, this::waitForNewProcessInstance);
    return this;
  }

  public BpmnTestEngine terminateProcessInstance() {
    taktClient.terminateElementInstance(activeProcessInstanceKey);
    return this;
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId, int count) {
    return waitUntilElementHasPassed(elementId, count, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId, int count, Duration duration) {
    Awaitility.await()
        .atMost(duration)
        .until(
            () -> getFlowNodeInstancesWithElementId(activeProcessInstanceKey, elementId),
            instances -> !instances.isEmpty() && instances.getFirst().getPassedCnt() == count);
    return this;
  }

  public List<FlowNodeInstanceDTO> getFlowNodeInstancesWithElementId(
      UUID processInstanceKey, String elementPath) {

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
            .filter(e -> e.getKey().getProcessInstanceKey().equals(processInstanceKey))
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

  public VariablesDTO getVariables(UUID processInstanceKey) {
    return variablesMap.get(processInstanceKey);
  }

  public BpmnTestEngine waitUntilActivityHasState(
      String elementId, ActtivityStateEnum state, Duration duration) {
    Awaitility.await()
        .atMost(duration)
        .until(
            () -> {
              List<FlowNodeInstanceDTO> flowNodeInstanceWithElementId =
                  getFlowNodeInstancesWithElementId(activeProcessInstanceKey, elementId);
              return flowNodeInstanceWithElementId.getFirst()
                      instanceof ActivityInstanceDTO activityInstance
                  && activityInstance.getState() == state;
            },
            Objects::nonNull);

    return this;
  }

  public BpmnTestEngine waitUntilElementIsActive(String elementId, Duration duration) {
    return waitUntilActivityHasState(elementId, ActtivityStateEnum.WAITING, duration);
  }

  public BpmnTestEngine waitUntilElementIsActive(String elementId) {
    return waitUntilElementIsActive(elementId, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasFailed(String elementId) {
    return waitUntilActivityHasState(elementId, ActtivityStateEnum.FAILED, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasTerminated(String elementId) {
    return waitUntilActivityHasState(elementId, ActtivityStateEnum.TERMINATED, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementIsWaiting(String elementId) {
    return waitUntilActivityHasState(elementId, ActtivityStateEnum.WAITING, DEFAULT_DURATION);
  }

  public BpmnTestEngine terminateElementInstance() {
    taktClient.terminateElementInstance(
        activeProcessInstanceKey, List.of(selectedFlowNodeInstance.getElementInstanceId()));
    return this;
  }

  public void reset() {
    this.processInstanceParentChildMap.clear();
    this.processInstanceMap.clear();
    this.flowNodeInstanceMap.clear();
    this.mutableClock.set(originalClock.instant());
  }
}
