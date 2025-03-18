package com.flomaestro.engine.pi.testengine;

import com.flomaestro.client.TaktClient;
import com.flomaestro.engine.generic.MutableClock;
import com.flomaestro.engine.generic.TopologyProducer;
import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.ActivityInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageEventTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionMessageEventTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowElementsDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.SubProcessDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
  private static final String TOPIC_TEST_PREFIX = "test_tenant.test_namespace.";
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(BpmnTestEngine.class);

  private TaktClient taktClient;

  private final Map<UUID, Set<UUID>> processInstanceParentChildMap = new ConcurrentHashMap<>();
  private final Map<UUID, ConcurrentLinkedQueue<ExternalTaskTriggerDTO>>
      externalTaskTriggerQueueMap = new ConcurrentHashMap<>();
  private final Map<ProcessDefinitionKey, ConcurrentLinkedQueue<ProcessInstanceTriggerDTO>>
      definitionToInstancesMap = new ConcurrentHashMap<>();
  private final Map<UUID, ProcessInstanceDTO> processInstanceMap = new ConcurrentHashMap<>();
  private final Map<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceMap =
      new ConcurrentHashMap<>();
  private final Map<UUID, VariablesDTO> variablesMap = new ConcurrentHashMap<>();
  private final Map<String, ConcurrentLinkedQueue<MessageEventDTO>> messageSubscriptionMap =
      new ConcurrentHashMap<>();
  private final Map<String, Consumer<ConsumerRecord<UUID, ExternalTaskTriggerDTO>>>
      externalTaskTriggerConsumers = new ConcurrentHashMap<>();
  private ProcessDefinitionDTO activeProcessDefintion;
  private UUID activeProcessInstanceKey;
  private ExternalTaskTriggerDTO activeExternalTaskTrigger;
  private ParsedDefinitionsDTO definitionsBeingDeployed;
  private final Clock originalClock;
  private final MutableClock mutableClock;
  private UUID latestInstantiatedProcessInstanceKey;
  private KafkaConsumerUtil<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerConsumer;
  private KafkaConsumerUtil<MessageEventKeyDTO, MessageEventDTO> messageEventConsumer;
  private KafkaProducerUtil<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter;
  private FlowNodeInstanceDTO selectedFlowNodeInstance;
  private final Map<String, List<String>> elementIdIndexMap = new HashMap<>();

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
    processInstanceTriggerEmitter.close();
  }

  public void init() {
    String kafkaBootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    try (AdminClient adminClient =
        AdminClient.create(Map.of("bootstrap.servers", kafkaBootstrapServers))) {
      List<NewTopic> topics =
          Arrays.stream(Topics.values())
              .map(topic -> new NewTopic(TOPIC_TEST_PREFIX + topic.getTopicName(), 5, (short) 1))
              .toList();
      adminClient.createTopics(topics);
    }

    try {
      taktClient =
          TaktClient.newClientBuilder()
              .withTenant("test_tenant")
              .withNamespace("test_namespace")
              .withBootstrapServers(kafkaBootstrapServers)
              .build();
      Consumer<ConsumerRecord<UUID, InstanceUpdateDTO>> consumer = BpmnTestEngine.this::consume;
      taktClient.registerInstanceUpdateConsumer(consumer);
      taktClient.start();

    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    processInstanceTriggerEmitter =
        new KafkaProducerUtil(
            TOPIC_TEST_PREFIX + Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.serializer().getClass().getName(),
            CBORObjectMapperSerializer.class.getName());
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
  }

  public void consumeProcessInstanceTrigger(
      ConsumerRecord<UUID, ProcessInstanceTriggerDTO> record) {
    ProcessInstanceTriggerDTO trigger = record.value();
    LOG.info("Received process instance trigger: " + trigger + " " + trigger.getClass().getName());
    if (trigger instanceof StartCommandDTO startCommand) {
      if (startCommand.getParentProcessInstanceKey() != null) {
        Set<UUID> uuids1 =
            processInstanceParentChildMap.computeIfAbsent(
                startCommand.getParentProcessInstanceKey(), k -> new ConcurrentSkipListSet<>());
        uuids1.add(startCommand.getProcessInstanceKey());
      }
      ConcurrentLinkedQueue<ProcessInstanceTriggerDTO> processInstanceKeyList =
          definitionToInstancesMap.computeIfAbsent(
              startCommand.getProcessDefinitionKey(), k -> new ConcurrentLinkedQueue<>());
      processInstanceKeyList.add(trigger);
    }
  }

  public void consumeMessageEvent(ConsumerRecord<MessageEventKeyDTO, MessageEventDTO> record) {
    MessageEventDTO messageEvent = record.value();
    LOG.info("Received message event: " + messageEvent);
    ConcurrentLinkedQueue<MessageEventDTO> messageEvents =
        messageSubscriptionMap.computeIfAbsent(
            messageEvent.getMessageName(), k -> new ConcurrentLinkedQueue<>());
    messageEvents.add(messageEvent);
  }

  public void consumeExternalTaskTrigger(ConsumerRecord<UUID, ExternalTaskTriggerDTO> record) {
    ExternalTaskTriggerDTO externalTaskTrigger = record.value();

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

  public void consume(ConsumerRecord<UUID, InstanceUpdateDTO> record) {
    InstanceUpdateDTO instanceUpdate = record.value();
    UUID processInstanceKey = record.key();
    if (instanceUpdate instanceof ProcessInstanceUpdateDTO processInstanceUpdate) {
      LOG.info("Received process instance update: " + processInstanceKey + " " + instanceUpdate);

      ProcessInstanceDTO processInstanceDTO =
          getProcessInstanceDTO(processInstanceKey, processInstanceUpdate);
      log.info(
          "Adding to process instance map {} {}",
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
      LOG.info("Received FlowNode instance update: " + instanceUpdate);

      FlowNodeInstanceKeyDTO key =
          new FlowNodeInstanceKeyDTO(
              processInstanceKey, flowNodeInstanceUpdate.getFlowNodeInstancePath());
      this.flowNodeInstanceMap.put(key, flowNodeInstanceUpdate.getFlowNodeInstance());

      VariablesDTO existingVariables =
          variablesMap.computeIfAbsent(key.getProcessInstanceKey(), k -> VariablesDTO.empty());
      existingVariables.getVariables().putAll(flowNodeInstanceUpdate.getVariables().getVariables());
    }
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

    Consumer<ConsumerRecord<UUID, ExternalTaskTriggerDTO>> externalTaskConsumer =
        this::consumeExternalTaskTrigger;

    if (externalTaskTriggerConsumers.get(
            definitionsBeingDeployed.getDefinitionsKey().getProcessDefinitionId())
        == null) {
      LOG.info(
          "Registering external task consumer for process definition "
              + definitionsBeingDeployed.getDefinitionsKey().getProcessDefinitionId());
      externalTaskTriggerConsumers.put(
          definitionsBeingDeployed.getDefinitionsKey().getProcessDefinitionId(),
          externalTaskConsumer);

      taktClient.registerExternalTaskTriggerConsumer(
          definitionsBeingDeployed.getDefinitionsKey().getProcessDefinitionId(),
          externalTaskConsumer);
    }

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

  public BpmnTestEngine startProcessInstance(VariablesDTO variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);

    UUID newProcessInstanceKey =
        taktClient.startProcess(processDefinitionKey.getProcessDefinitionId(), variables);

    log.info("Starting process instance {}", newProcessInstanceKey);
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

  public BpmnTestEngine waitUntilExternalTaskIsWaitingForResponse(String elementId) {
    return waitUntilExternalTaskIsWaitingForResponse(elementId, DEFAULT_DURATION);
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
                            .anyMatch(FlowNodeInstanceDTO::isWaiting));

    return this;
  }

  public BpmnTestEngine waitForNewProcessInstance() {
    UUID referenceProcessInstanceKey = latestInstantiatedProcessInstanceKey;
    activeProcessInstanceKey =
        Awaitility.await()
            .atMost(DEFAULT_DURATION)
            .until(
                () -> latestInstantiatedProcessInstanceKey,
                instance -> !Objects.equals(referenceProcessInstanceKey, instance));
    return this;
  }

  public BpmnTestEngine waitUntilChildProcessIsCompleted() {
    return waitUntilChildProcessesHaveState(1, ProcessInstanceState.COMPLETED);
  }

  public BpmnTestEngine waitUntilChildProcessIsStarted() {
    return waitUntilChildProcessesHaveState(1, ProcessInstanceState.ACTIVE);
  }

  public BpmnTestEngine waitUntilChildProcessIsTerminated() {
    return waitUntilChildProcessesHaveState(1, ProcessInstanceState.TERMINATED);
  }

  public BpmnTestEngine waitUntilChildProcessesHaveState(
      int expectedCount, ProcessInstanceState processInstanceState) {
    activeProcessInstanceKey =
        Awaitility.await()
            .atMost(DEFAULT_DURATION)
            .until(
                () -> {
                  Set<UUID> childKeys = processInstanceParentChildMap.get(activeProcessInstanceKey);
                  if (childKeys == null) {
                    return null;
                  }
                  if (childKeys.size() >= expectedCount) {
                    UUID childProcessInstanceKey = childKeys.iterator().next();
                    ProcessInstanceDTO childProcessInstance =
                        processInstanceMap.get(childProcessInstanceKey);
                    return childProcessInstance != null
                            && childProcessInstance.getFlowNodeInstances().getState()
                                == processInstanceState
                        ? childProcessInstanceKey
                        : null;
                  }
                  return null;
                },
                Objects::nonNull);
    return this;
  }

  public BpmnTestEngine andRespondWithSuccess(VariablesDTO variables) {
    taktClient
        .respondToExternalTask(activeExternalTaskTrigger)
        .respondSuccess(variables.getVariables());
    return this;
  }

  public BpmnTestEngine andRespondWithPromise(String newTimeout) {
    taktClient
        .respondToExternalTask(activeExternalTaskTrigger)
        .respondPromise(Duration.parse(newTimeout));
    return this;
  }

  public BpmnTestEngine andRespondWithFailure(
      boolean allowRetry, String name, String code, String message, VariablesDTO variables) {
    taktClient
        .respondToExternalTask(activeExternalTaskTrigger)
        .respondError(allowRetry, code, name, message, variables);
    return this;
  }

  public BpmnTestEngine andRespondWithEscalation(
      String name, String code, String message, VariablesDTO variables) {
    taktClient
        .respondToExternalTask(activeExternalTaskTrigger)
        .respondEscalation(name, message, code);
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
    log.info("Advanced the time by {} to {}", duration, Instant.now(mutableClock));
    return this;
  }

  public BpmnTestEngine setTime(Instant newInstant) {
    log.info("Setting the time to: " + newInstant);
    mutableClock.set(newInstant);
    return this;
  }

  public BpmnTestEngine doNotExpectNewProcessInstance() {
    Assertions.assertThrows(ConditionTimeoutException.class, this::waitForNewProcessInstance);
    return this;
  }

  public BpmnTestEngine terminateProcessInstance() {
    processInstanceTriggerEmitter.send(
        activeProcessInstanceKey, new TerminateTriggerDTO(activeProcessInstanceKey, List.of()));
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
                      e.getValue().getElementId()
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
      String receiveTaskMessage, String elementId, Set<String> correlationKeys) {
    return waitForMessageSubscription(
        receiveTaskMessage, elementId, correlationKeys, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitForMessageSubscription(
      String messageName, String elementId, Set<String> correlationKeys, Duration duration) {
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

  public ExternalTaskAssert assertThatExternalTask() {
    return new ExternalTaskAssert(activeExternalTaskTrigger, this);
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

  public BpmnTestEngine terminateElemeent() {
    processInstanceTriggerEmitter.send(
        activeProcessInstanceKey,
        new TerminateTriggerDTO(
            activeProcessInstanceKey, List.of(selectedFlowNodeInstance.getElementInstanceId())));
    return this;
  }

  public void reset() {
    this.mutableClock.set(originalClock.instant());
  }
}
