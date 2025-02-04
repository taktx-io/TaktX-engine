package com.flomaestro.engine.pi.testengine;

import com.flomaestro.engine.generic.FixedClockProducer;
import com.flomaestro.engine.generic.MutableClock;
import com.flomaestro.engine.generic.TopologyProducer;
import com.flomaestro.engine.pi.DebuggerUtil;
import com.flomaestro.takt.Topics;
import com.flomaestro.takt.dto.v_1_0_0.ActivityInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ActtivityStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageEventTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.CorrelationMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionMessageEventTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionsTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseResultDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskResponseType;
import com.flomaestro.takt.dto.v_1_0_0.ExternalTaskTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowNodeInstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceState;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.TerminateTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import com.flomaestro.takt.dto.v_1_0_0.XmlDefinitionsDTO;
import com.flomaestro.takt.xml.BpmnParser;
import io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;
import org.xml.sax.SAXException;

public class BpmnTestEngine implements KafkaConsumerRebalanceListener {

  private static final Logger LOG = Logger.getLogger(BpmnTestEngine.class);
  public static final Duration DEFAULT_DURATION;
  private static final String TOPIC_TEST_PREFIX = "test_tenant.test_namespace.";
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(BpmnTestEngine.class);

  static {
    if (DebuggerUtil.isDebuggerAttached()) {
      DEFAULT_DURATION = Duration.ofSeconds(10);
    } else {
      DEFAULT_DURATION = Duration.ofSeconds(10);
    }
  }

  private final Map<UUID, Set<UUID>> processInstanceParentChildMap = new HashMap<>();
  private final Map<UUID, ConcurrentLinkedQueue<ExternalTaskTriggerDTO>>
      externalTaskTriggerQueueMap = new HashMap<>();
  private final Map<ProcessDefinitionKey, ConcurrentLinkedQueue<ProcessInstanceTriggerDTO>>
      definitionToInstancesMap = new HashMap<>();
  private final Map<UUID, ProcessInstanceDTO> processInstanceMap = new HashMap<>();
  private final Map<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> flowNodeInstanceMap =
      new HashMap<>();
  private final Map<UUID, VariablesDTO> variablesMap = new HashMap<>();
  private final Map<String, ProcessDefinitionDTO> hashToDefinitionMap = new HashMap<>();
  private final Map<String, ConcurrentLinkedQueue<MessageEventDTO>> messageSubscriptionMap =
      new HashMap<>();
  private ProcessDefinitionDTO activeProcessDefintion;
  private UUID activeProcessInstanceKey;
  private ExternalTaskTriggerDTO activeExternalTaskTrigger;
  private ParsedDefinitionsDTO definitionsBeingDeployed;
  private final MutableClock mutableClock;
  private UUID latestInstantiatedProcessInstanceKey;
  private KafkaConsumerUtil<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerConsumer;
  private KafkaConsumerUtil<MessageEventKeyDTO, MessageEventDTO> messageEventConsumer;
  private KafkaConsumerUtil<UUID, ExternalTaskTriggerDTO> externalTaskTriggerConsumer;
  private KafkaConsumerUtil<UUID, InstanceUpdateDTO> instanceUpdateConsumer;
  private KafkaConsumerUtil<ProcessDefinitionKey, ProcessDefinitionDTO>
      processDefinitionParsedConsumer;
  private KafkaProducerUtil<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter;
  private KafkaProducerUtil<String, DefinitionsTriggerDTO> processDefinitionsTriggerEmitter;
  private KafkaProducerUtil<MessageEventKeyDTO, MessageEventDTO> messageEventEmitter;
  private FlowNodeInstanceDTO selectedFlowNodeInstance;

  public BpmnTestEngine(Clock clock) {
    this.mutableClock = (MutableClock) clock;
  }

  private static @NotNull ProcessInstanceDTO getProcessInstanceDTO(
      UUID processInstanceKey, ProcessInstanceUpdateDTO processInstanceUpdate) {
    return new ProcessInstanceDTO(
        processInstanceKey,
        processInstanceUpdate.getParentProcessInstanceKey(),
        processInstanceUpdate.getFlowNodeInstances(),
        processInstanceUpdate.getParentElementIdPath(),
        processInstanceUpdate.getParentElementInstancePath(),
        processInstanceUpdate.getProcessDefinitionKey(),
        false,
        Set.of());
  }

  public void close() {
    LOG.info("Closing bpmn test engine");
    processInstanceTriggerConsumer.stop();
    messageEventConsumer.stop();
    externalTaskTriggerConsumer.stop();
    instanceUpdateConsumer.stop();
    processDefinitionParsedConsumer.stop();
    messageEventEmitter.close();
    processDefinitionsTriggerEmitter.close();
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

    processInstanceTriggerEmitter =
        new KafkaProducerUtil(
            TOPIC_TEST_PREFIX + Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.serializer().getClass().getName(),
            CBORObjectMapperSerializer.class.getName());
    processDefinitionsTriggerEmitter =
        new KafkaProducerUtil<>(
            TOPIC_TEST_PREFIX + Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName(),
            StringSerializer.class.getName(),
            CBORObjectMapperSerializer.class.getName());
    messageEventEmitter =
        new KafkaProducerUtil<>(
            TOPIC_TEST_PREFIX + Topics.MESSAGE_EVENT_TOPIC.getTopicName(),
            CBORObjectMapperSerializer.class.getName(),
            CBORObjectMapperSerializer.class.getName());
    processInstanceTriggerConsumer =
        new KafkaConsumerUtil<>(
            "test-group",
            TOPIC_TEST_PREFIX + Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
            ProcessInstanceTriggerDeserializer.class.getName(),
            this::consume);
    messageEventConsumer =
        new KafkaConsumerUtil<>(
            "test-group",
            TOPIC_TEST_PREFIX + Topics.MESSAGE_EVENT_TOPIC.getTopicName(),
            MessageEventKeyDeserializer.class.getName(),
            MessageEventDeserializer.class.getName(),
            this::consume);
    externalTaskTriggerConsumer =
        new KafkaConsumerUtil<>(
            "test-group",
            TOPIC_TEST_PREFIX + Topics.EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName(),
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
            ExternalTaskTriggerDeserializer.class.getName(),
            this::consume);
    instanceUpdateConsumer =
        new KafkaConsumerUtil<>(
            "test-group",
            TOPIC_TEST_PREFIX + Topics.INSTANCE_UPDATE_TOPIC.getTopicName(),
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
            InstanceUpdateDeserializer.class.getName(),
            this::consume);
    processDefinitionParsedConsumer =
        new KafkaConsumerUtil<>(
            "test-group",
            TOPIC_TEST_PREFIX + Topics.PROCESS_DEFINITION_ACTIVATION_TOPIC.getTopicName(),
            ProcessDefinitionKeyDeserializer.class.getName(),
            ProcessDefinitionDeserializer.class.getName(),
            this::consume);
    reset();
  }

  public void consume(UUID key2, ProcessInstanceTriggerDTO trigger) {
    LOG.info("Received flow element trigger: " + trigger);
    if (trigger instanceof StartCommandDTO startCommand) {
      Set<UUID> uuids1 =
          processInstanceParentChildMap.computeIfAbsent(
              startCommand.getParentProcessInstanceKey(), k -> new HashSet<>());
      uuids1.add(startCommand.getProcessInstanceKey());
      ConcurrentLinkedQueue<ProcessInstanceTriggerDTO> processInstanceKeyList =
          definitionToInstancesMap.computeIfAbsent(
              startCommand.getProcessDefinitionKey(), k -> new ConcurrentLinkedQueue<>());
      processInstanceKeyList.add(trigger);
    }
  }

  public void consume(MessageEventKeyDTO key, MessageEventDTO messageEvent) {
    LOG.info("Received message event: " + messageEvent);
    ConcurrentLinkedQueue<MessageEventDTO> messageEvents =
        messageSubscriptionMap.computeIfAbsent(
            messageEvent.getMessageName(), k -> new ConcurrentLinkedQueue<>());
    messageEvents.add(messageEvent);
  }

  public void consume(UUID key, ExternalTaskTriggerDTO externalTaskTrigger) {
    LOG.info("Received external task trigger: " + externalTaskTrigger);
    ConcurrentLinkedQueue<ExternalTaskTriggerDTO> externalTaskTriggers =
        externalTaskTriggerQueueMap.computeIfAbsent(
            externalTaskTrigger.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    externalTaskTriggers.add(externalTaskTrigger);
  }

  public void consume(UUID processInstanceKey, InstanceUpdateDTO instanceUpdate) {
    if (instanceUpdate instanceof ProcessInstanceUpdateDTO processInstanceUpdate) {
      LOG.info("Received process instance update: " + instanceUpdate);

      ProcessInstanceDTO processInstanceDTO =
          getProcessInstanceDTO(processInstanceKey, processInstanceUpdate);
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

  public void consume(ProcessDefinitionKey key, ProcessDefinitionDTO processDefinition) {
    LOG.info(
        "Received process definition: "
            + processDefinition
            + " "
            + processDefinition.getDefinitions().getDefinitionsKey().getHash());
    if (processDefinition.getState() == ProcessDefinitionStateEnum.ACTIVE) {
      hashToDefinitionMap.put(
          processDefinition.getDefinitions().getDefinitionsKey().getHash(), processDefinition);
    }
  }

  public ExternalTaskTriggerDTO pollExternalTask() {
    ConcurrentLinkedQueue<ExternalTaskTriggerDTO> externalTaskTriggers =
        externalTaskTriggerQueueMap.get(activeProcessInstanceKey);
    if (externalTaskTriggers == null) {
      return null;
    }
    return externalTaskTriggers.poll();
  }

  public void triggerExternalTaskResponse(
      UUID processInstanceKey,
      ExternalTaskTriggerDTO externalTaskTrigger,
      ExternalTaskResponseResultDTO externalTaskResponseResult,
      VariablesDTO variables) {
    processInstanceTriggerEmitter.send(
        processInstanceKey,
        new ExternalTaskResponseTriggerDTO(
            externalTaskTrigger.getProcessInstanceKey(),
            externalTaskTrigger.getElementInstanceIdPath(),
            externalTaskResponseResult,
            variables));
  }

  public BpmnTestEngine deployProcessDefinition(String filename) throws IOException {
    LOG.info("Deploying process definition: " + filename);
    String xml = IOUtils.toString(BpmnTestEngine.class.getResourceAsStream(filename));
    definitionsBeingDeployed = BpmnParser.parse(xml);
    hashToDefinitionMap.clear();

    processDefinitionsTriggerEmitter.send(
        definitionsBeingDeployed.getDefinitionsKey().getProcessDefinitionId(),
        new XmlDefinitionsDTO(xml));
    return this;
  }

  public BpmnTestEngine waitForProcessDeployment() {
    return this.waitForProcessDeployment(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitForProcessDeployment(Duration duration) {
    activeProcessDefintion =
        Awaitility.await()
            .atMost(duration)
            .until(
                () ->
                    hashToDefinitionMap.values().iterator().hasNext()
                        ? hashToDefinitionMap.values().iterator().next()
                        : null,
                obj -> {
                  if (obj == null) {
                    return false;
                  }
                  String actualDeployed =
                      obj.getDefinitions().getDefinitionsKey().getProcessDefinitionId();
                  String expectedDeployed =
                      definitionsBeingDeployed.getDefinitionsKey().getProcessDefinitionId();
                  LOG.info(
                      "Comparing actual deployed process "
                          + actualDeployed
                          + " with "
                          + expectedDeployed);
                  return actualDeployed.equals(expectedDeployed);
                });

    return this;
  }

  public BpmnTestEngine deployProcessDefinitionAndWait(String filename)
      throws JAXBException,
          NoSuchAlgorithmException,
          IOException,
          ParserConfigurationException,
          SAXException {
    return deployProcessDefinitionAndWait(filename, DEFAULT_DURATION);
  }

  public BpmnTestEngine deployProcessDefinitionAndWait(String filename, Duration duration)
      throws JAXBException,
          NoSuchAlgorithmException,
          IOException,
          ParserConfigurationException,
          SAXException {
    deployProcessDefinition(filename);
    waitForProcessDeployment(duration);
    return this;
  }

  public BpmnTestEngine startProcessInstance(VariablesDTO variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);
    UUID processInstanceKey = UUID.randomUUID();
    StartCommandDTO startCommand =
        new StartCommandDTO(
            processInstanceKey,
            null,
            List.of(),
            List.of(),
            ProcessDefinitionKey.of(activeProcessDefintion),
            variables);
    processInstanceTriggerEmitter.send(processInstanceKey, startCommand);

    try {
      activeProcessInstanceKey =
          Awaitility.await()
              .until(
                  () -> {
                    ConcurrentLinkedQueue<ProcessInstanceTriggerDTO> flowElementTriggers =
                        definitionToInstancesMap.get(processDefinitionKey);
                    if (flowElementTriggers == null || flowElementTriggers.isEmpty()) {
                      return null;
                    }
                    ProcessInstanceTriggerDTO poll = flowElementTriggers.poll();
                    if (poll instanceof StartCommandDTO startNewProcessInstanceTrigger
                        && startNewProcessInstanceTrigger
                            .getProcessDefinitionKey()
                            .equals(processDefinitionKey)) {
                      return poll.getProcessInstanceKey();
                    }
                    return null;
                  },
                  Objects::nonNull);
    } catch (ConditionTimeoutException e) {
      LOG.error("Error waiting for process instance to start: " + e);
      throw new IllegalStateException(e);
    }
    try {
      Awaitility.await()
          .until(() -> processInstanceMap.get(activeProcessInstanceKey), Objects::nonNull);
    } catch (ConditionTimeoutException e) {
      LOG.error("Error waiting for process instance to start: " + e);
      throw new IllegalStateException(e);
    }

    return this;
  }

  public BpmnTestEngine waitUntilExternalTaskIsWaitingForResponse(String elementId) {
    activeExternalTaskTrigger =
        Awaitility.await()
            .atMost(DEFAULT_DURATION)
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

  public BpmnTestEngine andRespondWithSuccess(VariablesDTO of) {
    triggerExternalTaskResponse(
        activeProcessInstanceKey,
        activeExternalTaskTrigger,
        new ExternalTaskResponseResultDTO(
            ExternalTaskResponseType.SUCCESS, true, null, null, null, 0L),
        of);
    return this;
  }

  public BpmnTestEngine andRespondWithPromise(String newTimeout) {
    triggerExternalTaskResponse(
        activeProcessInstanceKey,
        activeExternalTaskTrigger,
        new ExternalTaskResponseResultDTO(
            ExternalTaskResponseType.PROMISE,
            true,
            null,
            null,
            null,
            Duration.parse(newTimeout).toMillis()),
        VariablesDTO.empty());
    return this;
  }

  public BpmnTestEngine andRespondWithFailure(
      boolean allowRetry, String name, String code, String message, VariablesDTO variables) {
    triggerExternalTaskResponse(
        activeProcessInstanceKey,
        activeExternalTaskTrigger,
        new ExternalTaskResponseResultDTO(
            ExternalTaskResponseType.ERROR, allowRetry, name, message, code, 0L),
        variables);
    return this;
  }

  public BpmnTestEngine andRespondWithEscalation(
      String name, String code, String message, VariablesDTO variables) {
    triggerExternalTaskResponse(
        activeProcessInstanceKey,
        activeExternalTaskTrigger,
        new ExternalTaskResponseResultDTO(
            ExternalTaskResponseType.ESCALATION, true, name, message, code, 0L),
        variables);
    return this;
  }

  public BpmnTestEngine waitUntilCompleted() {
    return waitUntilCompleted(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilCompleted(Duration duration) {
    try {
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
    } catch (ConditionTimeoutException e) {
      LOG.error("Process instance: " + activeProcessInstanceKey + " did not complete in time");
      LOG.error(processInstanceMap);
      throw e;
    }
    return this;
  }

  public ProcessInstanceAssert assertThatProcess() {
    return new ProcessInstanceAssert(activeProcessInstanceKey, this);
  }

  public void reset() {
    processInstanceParentChildMap.clear();
    externalTaskTriggerQueueMap.clear();
    definitionToInstancesMap.clear();
    processInstanceMap.clear();
    flowNodeInstanceMap.clear();
    variablesMap.clear();
    hashToDefinitionMap.clear();
    messageSubscriptionMap.clear();

    activeProcessDefintion = null;
    activeProcessInstanceKey = null;
    activeExternalTaskTrigger = null;
    mutableClock.set(Instant.parse(FixedClockProducer.INITIAL_TIME));
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

    List<String> elementPathList = Stream.of(elementPath.split("/")).toList();
    Map<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> filteredByProcessInstance =
        flowNodeInstanceMap.entrySet().stream()
            .filter(e -> e.getKey().getProcessInstanceKey().equals(processInstanceKey))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    Map<FlowNodeInstanceKeyDTO, FlowNodeInstanceDTO> filteredByElementIdOnIndex =
        filteredByProcessInstance;

    for (int index = 0; index < elementPathList.size(); index++) {
      int currentIndex = index;
      filteredByElementIdOnIndex =
          filteredByProcessInstance.entrySet().stream()
              .filter(e -> e.getValue().getElementId().equals(elementPathList.get(currentIndex)))
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    return new ArrayList<>(filteredByElementIdOnIndex.values());
  }

  public BpmnTestEngine sendMessage(String messageName, VariablesDTO variables) {
    LOG.info("Sending message: " + messageName);
    DefinitionMessageEventTriggerDTO messageEvent =
        new DefinitionMessageEventTriggerDTO(messageName, variables);
    messageEventEmitter.send(messageEvent.toMessageEventKey(), messageEvent);
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
                  return correlationMessageSubscription.getElementIdPath().contains(elementId)
                      && remainingCorrelationKeys.isEmpty();
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
    messageEventEmitter.send(messageEvent.toMessageEventKey(), messageEvent);
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
}
