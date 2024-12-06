package nl.qunit.bpmnmeister.engine.pi.testengine;


import io.quarkus.kafka.client.serialization.ObjectMapperSerializer;
import io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.engine.generic.TopologyProducer;
import nl.qunit.bpmnmeister.engine.pd.MutableClock;
import nl.qunit.bpmnmeister.engine.pi.DebuggerUtil;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.DefinitionsTrigger;
import nl.qunit.bpmnmeister.pd.model.ParsedDefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.XmlDefinitionsDTO;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.CorrelationMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.DefinitionMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseResult;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTypeEnum;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowNodeInstanceUpdate;
import nl.qunit.bpmnmeister.pi.InstanceUpdate;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.state.ActivityInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.ActtivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import nl.qunit.bpmnmeister.pi.state.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;
import nl.qunit.bpmnmeister.pi.state.WithFlowNodeInstancesDTO;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;
import org.xml.sax.SAXException;

public class BpmnTestEngine implements KafkaConsumerRebalanceListener {

  private static final Logger LOG = Logger.getLogger(BpmnTestEngine.class);
  public static final Duration DEFAULT_DURATION;
  private static final String TOPIC_TEST_PREFIX = "test_tenant.test_namespace.";

  static {
    if (DebuggerUtil.isDebuggerAttached()) {
      DEFAULT_DURATION = Duration.ofSeconds(10);
    } else {
      DEFAULT_DURATION = Duration.ofSeconds(10);
    }
  }

  private final Map<UUID, Set<UUID>> processInstanceParentChildMap = new HashMap<>();
  private final Map<UUID, ConcurrentLinkedQueue<ExternalTaskTrigger>> externalTaskTriggerQueueMap = new HashMap<>();
  private final Map<ProcessDefinitionKey, ConcurrentLinkedQueue<ProcessInstanceTrigger>> definitionToInstancesMap = new HashMap<>();
  private final Map<UUID, ProcessInstanceDTO> processInstanceMap = new HashMap<>();
  private final Map<UUID, Map<UUID, Map<UUID, FlowNodeInstanceDTO>>> flowNodeInstanceMap = new HashMap<>();
  private final Map<UUID, VariablesDTO> variablesMap = new HashMap<>();
  private final Map<String, ProcessDefinitionDTO> hashToDefinitionMap = new HashMap<>();
  private final Map<String, ConcurrentLinkedQueue<MessageEvent>> messageSubscriptionMap = new HashMap<>();
  private ProcessDefinitionDTO activeProcessDefintion;
  private UUID activeProcessInstanceKey;
  private ExternalTaskTrigger activeExternalTaskTrigger;
  private ParsedDefinitionsDTO definitionsBeingDeployed;
  private final MutableClock mutableClock;
  private UUID latestInstantiatedProcessInstanceKey;
  private KafkaConsumerUtil<UUID, ProcessInstanceTrigger> processInstanceTriggerConsumer;
  private KafkaConsumerUtil<MessageEventKey, MessageEvent> messageEventConsumer;
  private KafkaConsumerUtil<UUID, ExternalTaskTrigger> externalTaskTriggerConsumer;
  private KafkaConsumerUtil<UUID, InstanceUpdate> instanceUpdateConsumer;
  private KafkaConsumerUtil<ProcessDefinitionKey, ProcessDefinitionDTO> processDefinitionParsedConsumer;
  private KafkaProducerUtil<UUID, ProcessInstanceTrigger> processInstanceTriggerEmitter;
  private KafkaProducerUtil<String, DefinitionsTrigger> processDefinitionsTriggerEmitter;
  private KafkaProducerUtil<MessageEventKey, MessageEvent> messageEventEmitter;
  private FlowNodeInstanceDTO selectedFlowNodeInstance;

  public BpmnTestEngine(Clock clock) {
    this.mutableClock = (MutableClock) clock;
  }

  public void init() {
    String kafkaBootstrapServers = ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    try (AdminClient adminClient = AdminClient.create(
        Map.of("bootstrap.servers", kafkaBootstrapServers))) {
      List<NewTopic> topics = Arrays.stream(Topics.values())
          .map(topic -> new NewTopic(TOPIC_TEST_PREFIX + topic.getTopicName(), 5, (short) 1))
          .toList();
      adminClient.createTopics(
          topics);
    }

    processInstanceTriggerEmitter = new KafkaProducerUtil(
        TOPIC_TEST_PREFIX + Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
        TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.serializer().getClass().getName(),
        ObjectMapperSerializer.class.getName());
    processDefinitionsTriggerEmitter = new KafkaProducerUtil<>(
        TOPIC_TEST_PREFIX + Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName(),
        StringSerializer.class.getName(),
        ObjectMapperSerializer.class.getName());
    messageEventEmitter = new KafkaProducerUtil<>(TOPIC_TEST_PREFIX + Topics.MESSAGE_EVENT_TOPIC.getTopicName(),
        ObjectMapperSerializer.class.getName(),
        ObjectMapperSerializer.class.getName());
    processInstanceTriggerConsumer = new KafkaConsumerUtil<>("test-group",
        TOPIC_TEST_PREFIX + Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
        TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
        ProcessInstanceTriggerDeserializer.class.getName(),
        this::consume);
    messageEventConsumer = new KafkaConsumerUtil<>("test-group",
        TOPIC_TEST_PREFIX + Topics.MESSAGE_EVENT_TOPIC.getTopicName(),
        MessageEventKeyDeserializer.class.getName(),
        MessageEventDeserializer.class.getName(),
        this::consume);
    externalTaskTriggerConsumer = new KafkaConsumerUtil<>("test-group",
        TOPIC_TEST_PREFIX + Topics.EXTERNAL_TASK_TRIGGER_TOPIC.getTopicName(),
        TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
        ExternalTaskTriggerDeserializer.class.getName(),
        this::consume);
    instanceUpdateConsumer = new KafkaConsumerUtil<>("test-group",
        TOPIC_TEST_PREFIX + Topics.INSTANCE_UPDATE_TOPIC.getTopicName(),
        TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
        InstanceUpdateDeserializer.class.getName(),
        this::consume);
    processDefinitionParsedConsumer = new KafkaConsumerUtil<>("test-group",
        TOPIC_TEST_PREFIX + Topics.PROCESS_DEFINITION_PARSED_TOPIC.getTopicName(),
        ProcessDefinitionKeyDeserializer.class.getName(),
        ProcessDefinitionDeserializer.class.getName(),
        this::consume);
    clear();
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

  public void consume(ProcessInstanceTrigger trigger) {
    LOG.info("Received flow element trigger: " + trigger);
    if (trigger instanceof StartNewProcessInstanceTrigger startNewProcessInstanceTrigger) {
      ProcessDefinitionKey processDefinitionKey = nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey.of(
          startNewProcessInstanceTrigger.getProcessDefinition());
      Set<UUID> uuids1 = processInstanceParentChildMap.computeIfAbsent(
          startNewProcessInstanceTrigger.getParentProcessInstanceKey(), k -> new HashSet<>());
      uuids1.add(startNewProcessInstanceTrigger.getProcessInstanceKey());
      ConcurrentLinkedQueue<ProcessInstanceTrigger> processInstanceKeyList = definitionToInstancesMap.computeIfAbsent(
          processDefinitionKey, k -> new ConcurrentLinkedQueue<>());
      processInstanceKeyList.add(trigger);
    }
  }

  public void consume(MessageEvent messageEvent) {
    LOG.info("Received message event: " + messageEvent);
    ConcurrentLinkedQueue<MessageEvent> messageEvents = messageSubscriptionMap.computeIfAbsent(
        messageEvent.getMessageName(), k -> new ConcurrentLinkedQueue<>());
    messageEvents.add(messageEvent);
  }

  public void consume(ExternalTaskTrigger externalTaskTrigger) {
    LOG.info("Received external task trigger: " + externalTaskTrigger);
    ConcurrentLinkedQueue<ExternalTaskTrigger> externalTaskTriggers = externalTaskTriggerQueueMap.computeIfAbsent(
        externalTaskTrigger.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    externalTaskTriggers.add(externalTaskTrigger);
  }

  public void consume(InstanceUpdate instanceUpdate) {
    if (instanceUpdate instanceof ProcessInstanceUpdate processInstanceUpdate) {
      LOG.info("Received process instance update: " + instanceUpdate);

      ProcessInstanceDTO processInstanceDTO = getProcessInstanceDTO(processInstanceUpdate);
      ProcessInstanceDTO previousProcessInstance = processInstanceMap.put(instanceUpdate.getProcessInstanceKey(),
          processInstanceDTO);
      if (previousProcessInstance == null) {
        latestInstantiatedProcessInstanceKey = processInstanceDTO.getProcessInstanceKey();
      }
    } else if (instanceUpdate instanceof FlowNodeInstanceUpdate flowNodeInstanceUpdate) {
      LOG.info("Received FlowNode instance update: " + instanceUpdate);

      Map<UUID, Map<UUID, FlowNodeInstanceDTO>> flowNodeInstancesDTOMap =
          flowNodeInstanceMap.computeIfAbsent(flowNodeInstanceUpdate.getProcessInstanceKey(), id -> new HashMap<>());
      Map<UUID, FlowNodeInstanceDTO> instanceMap =
          flowNodeInstancesDTOMap.computeIfAbsent(flowNodeInstanceUpdate.getFlowNodeInstancesId(), id -> new HashMap<>());

      instanceMap.put(flowNodeInstanceUpdate.getFlowNodeInstance().getElementInstanceId(), flowNodeInstanceUpdate.getFlowNodeInstance());
      variablesMap.put(flowNodeInstanceUpdate.getProcessInstanceKey(), flowNodeInstanceUpdate.getVariables());
    }
  }

  private static @NotNull ProcessInstanceDTO getProcessInstanceDTO(ProcessInstanceUpdate processInstanceUpdate) {
    return new ProcessInstanceDTO(
        processInstanceUpdate.getProcessInstanceKey(),
        processInstanceUpdate.getParentProcessInstanceKey(),
        processInstanceUpdate.getParentElementIdPath(),
        processInstanceUpdate.getParentElementInstancePath(),
        processInstanceUpdate.getProcessDefinitionKey(),
        processInstanceUpdate.getFlowNodeInstances());
  }

  public void consume(ProcessDefinitionDTO processDefinition) {
    LOG.info("Received process definition: " + processDefinition + " " + processDefinition.getDefinitions().getDefinitionsKey().getHash()) ;
    hashToDefinitionMap.put(processDefinition.getDefinitions().getDefinitionsKey().getHash(), processDefinition);
  }

  public ExternalTaskTrigger pollExternalTask() {
    ConcurrentLinkedQueue<ExternalTaskTrigger> externalTaskTriggers = externalTaskTriggerQueueMap.get(activeProcessInstanceKey);
    if (externalTaskTriggers == null) {
      return null;
    }
    return externalTaskTriggers.poll();
  }


  public void triggerExternalTaskResponse(UUID processInstanceKey, ExternalTaskTrigger externalTaskTrigger,
      ExternalTaskResponseResult externalTaskResponseResult, VariablesDTO variables) {
    processInstanceTriggerEmitter.send(processInstanceKey,
          new ExternalTaskResponseTrigger(externalTaskTrigger.getProcessInstanceKey(),
          externalTaskTrigger.getElementIdPath(), externalTaskTrigger.getElementInstanceIdPath(), externalTaskResponseResult, variables)
      );
  }


  public BpmnTestEngine deployProcessDefinition(String filename)
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    LOG.info("Deploying process definition: " + filename);
    String xml = IOUtils.toString(BpmnTestEngine.class.getResourceAsStream(filename));
    definitionsBeingDeployed = BpmnParser.parse(xml);
    hashToDefinitionMap.clear();

    processDefinitionsTriggerEmitter.send(definitionsBeingDeployed.getDefinitionsKey().getProcessDefinitionId(),
        new XmlDefinitionsDTO(xml));
    return this;
  }

  public BpmnTestEngine waitForProcessDeployment() {
    return this.waitForProcessDeployment(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitForProcessDeployment(Duration duration) {
    activeProcessDefintion = Awaitility.await().atMost(duration)
        .until(() ->
                hashToDefinitionMap.values().iterator().hasNext() ? hashToDefinitionMap.values().iterator().next() : null,
            obj -> obj != null && obj.getDefinitions().getDefinitionsKey().getProcessDefinitionId()
                .equals(definitionsBeingDeployed.getDefinitionsKey().getProcessDefinitionId()));

    return this;
  }

  public BpmnTestEngine deployProcessDefinitionAndWait(String filename)
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    return deployProcessDefinitionAndWait(filename, DEFAULT_DURATION);
  }

  public BpmnTestEngine deployProcessDefinitionAndWait(String filename, Duration duration)
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    deployProcessDefinition(filename);
    waitForProcessDeployment(duration);
    return this;
  }

  public BpmnTestEngine startProcessInstanceNoWait(VariablesDTO variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);
    StartCommand startCommand = new StartCommand(
        Constants.NONE_UUID,
        Constants.NONE_UUID,
        Constants.NONE,
        List.of(),
        List.of(),
        activeProcessDefintion.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
        variables);
    processDefinitionsTriggerEmitter.send(processDefinitionKey.getProcessDefinitionId(), startCommand);
    return this;
  }

  public BpmnTestEngine startProcessInstance(VariablesDTO variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);
    StartCommand startCommand = new StartCommand(
        Constants.NONE_UUID,
        Constants.NONE_UUID,
        Constants.NONE,
        List.of(),
        List.of(),
        activeProcessDefintion.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
        variables);
    processDefinitionsTriggerEmitter.send(processDefinitionKey.getProcessDefinitionId(), startCommand);

    try {
       activeProcessInstanceKey = Awaitility.await()
          .until(() -> {
            ConcurrentLinkedQueue<ProcessInstanceTrigger> flowElementTriggers = definitionToInstancesMap.get(
                processDefinitionKey);
            if (flowElementTriggers == null || flowElementTriggers.isEmpty()) {
              return null;
            }
            ProcessInstanceTrigger poll = flowElementTriggers.poll();
            if (poll instanceof StartNewProcessInstanceTrigger startNewProcessInstanceTrigger &&
                ProcessDefinitionKey.of(startNewProcessInstanceTrigger.getProcessDefinition())
                    .equals(processDefinitionKey)) {
              return poll.getProcessInstanceKey();
            }
            return null;
          }, Objects::nonNull);
    } catch (ConditionTimeoutException e) {
      LOG.error("Error waiting for process instance to start: " + e);
      throw new IllegalStateException(e);
    }
    try {
      Awaitility.await()
          .until(() -> processInstanceMap.get(activeProcessInstanceKey),
              Objects::nonNull);
    } catch (ConditionTimeoutException e) {
      LOG.error("Error waiting for process instance to start: " + e);
      throw new IllegalStateException(e);
    }

    return this;
  }

  public BpmnTestEngine waitUntilExternalTaskIsWaitingForResponse(String elementId) {
    activeExternalTaskTrigger = Awaitility.await().atMost(DEFAULT_DURATION)
        .until(this::pollExternalTask,
            externalTaskTrigger -> externalTaskTrigger != null &&
                                   externalTaskTrigger.getElementIdPath().contains(elementId));
    return this;
  }


  public BpmnTestEngine waitForNewProcessInstance() {
    UUID referenceProcessInstanceKey = latestInstantiatedProcessInstanceKey;
    activeProcessInstanceKey = Awaitility.await().atMost(DEFAULT_DURATION)
        .until(() -> latestInstantiatedProcessInstanceKey,
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

  public BpmnTestEngine waitUntilChildProcessesHaveState(int expectedCount, ProcessInstanceState processInstanceState) {
    activeProcessInstanceKey = Awaitility.await().atMost(DEFAULT_DURATION)
        .until(() -> {
          Set<UUID> childKeys = processInstanceParentChildMap.get(activeProcessInstanceKey);
          if (childKeys == null) {
            return null;
          }
          if (childKeys.size() >= expectedCount) {
            UUID childProcessInstanceKey = childKeys.iterator().next();
            ProcessInstanceDTO childProcessInstance = processInstanceMap.get(childProcessInstanceKey);
            return
                childProcessInstance != null && childProcessInstance.getFlowNodeInstances().getState() == processInstanceState
                    ? childProcessInstanceKey : null;
          }
          return null;
        }, Objects::nonNull);
    return this;
  }

  public BpmnTestEngine andRespondWithSuccess(VariablesDTO of) {
    triggerExternalTaskResponse(activeProcessInstanceKey, activeExternalTaskTrigger, new ExternalTaskResponseResult(ExternalTaskResponseTypeEnum.SUCCESS, true,
        Constants.NONE, Constants.NONE, Constants.NONE), of);
    return this;
  }

  public BpmnTestEngine andRespondWithFailure(boolean allowRetry, String name, String code, String message, VariablesDTO variables) {
    triggerExternalTaskResponse(activeProcessInstanceKey,
        activeExternalTaskTrigger, new ExternalTaskResponseResult(ExternalTaskResponseTypeEnum.ERROR, allowRetry, name, message, code), variables);
    return this;
  }

  public BpmnTestEngine andRespondWithEscalation(String name, String code, String message, VariablesDTO variables) {
    triggerExternalTaskResponse(activeProcessInstanceKey,
        activeExternalTaskTrigger, new ExternalTaskResponseResult(ExternalTaskResponseTypeEnum.ESCALATION, true, name, message, code), variables);
    return this;
  }

  public BpmnTestEngine waitUntilCompleted() {
    return waitUntilCompleted(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilCompleted(Duration duration) {
    try {
      Awaitility.await()
          .atMost(duration)
          .until(() -> {
            if (activeProcessInstanceKey != null && processInstanceMap.get(activeProcessInstanceKey).getFlowNodeInstances().getState()
                .isFinished()) {
              return activeProcessInstanceKey;
            }
            return null;
          }, Objects::nonNull);
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

  public void clear() {
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
  }

  public BpmnTestEngine parentProcess() {
    ProcessInstanceDTO processInstanceDTO = processInstanceMap.get(activeProcessInstanceKey);

    activeProcessInstanceKey = processInstanceDTO.getParentProcessInstanceKey();
    return this;
  }

  public ProcessInstanceAssert assertThatParentProcess() {
    ProcessInstanceDTO processInstanceDTO = processInstanceMap.get(activeProcessInstanceKey);
    ProcessInstanceDTO parentProcessInstance = processInstanceMap.get(processInstanceDTO.getParentProcessInstanceKey());
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
    return this;
  }
  public BpmnTestEngine setTime(Instant newInstant) {
    mutableClock.set(newInstant);
    return this;
  }

  public BpmnTestEngine doNotExpectNewProcessInstance() {
    Assertions.assertThrows(ConditionTimeoutException.class, this::waitForNewProcessInstance);
    return this;
  }

  public BpmnTestEngine terminateProcessInstance() {
    processInstanceTriggerEmitter.send(activeProcessInstanceKey,
        new TerminateTrigger(activeProcessInstanceKey, List.of()));
    return this;
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId) {
    return waitUntilElementHasPassed(elementId, 1, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId, int count) {
    return waitUntilElementHasPassed(elementId, count, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId, int count, Duration duration) {
    Awaitility.await()
        .atMost(duration)
        .until(() -> getFlowNodeInstancesWithElementId(
            activeProcessInstanceKey,
                elementId,
                count),
            instances -> !instances.isEmpty());
    return this;
  }

  public List<FlowNodeInstanceDTO> getFlowNodeInstancesWithElementId(UUID processInstanceKey, String elementId, int... count) {
    ProcessInstanceDTO processInstanceDTO = processInstanceMap.get(processInstanceKey);

      return getFlowNodeInstancesWithElementId(processInstanceKey, processInstanceDTO.getFlowNodeInstances().getFlowNodeInstancesId(), elementId,
          0, count);
  }

  public List<FlowNodeInstanceDTO> getFlowNodeInstancesWithElementId(UUID processInstanceKey, UUID flowNodeInstancesId, String elementId,
      int index, int... count) {
    Map<UUID, Map<UUID, FlowNodeInstanceDTO>>flowNodeInstancesMap =
        flowNodeInstanceMap.get(processInstanceKey);
    Map<UUID, FlowNodeInstanceDTO> instanceMap = flowNodeInstancesMap.get(flowNodeInstancesId);
    String[] split = elementId.split("/");
    String elementIdSubPath = split[index];
    int checkCnt = index >= count.length ? count[count.length - 1] : count[index];
    return instanceMap.values().stream()
        .filter(i -> i.getElementId().equals(elementIdSubPath) && i.getPassedCnt() >= checkCnt).flatMap(flowNodeInstanceDTO -> {
          if (index < split.length - 1 && flowNodeInstanceDTO instanceof WithFlowNodeInstancesDTO withFlowNodeInstances) {
            return getFlowNodeInstancesWithElementId(processInstanceKey, withFlowNodeInstances.getFlowNodeInstances().getFlowNodeInstancesId(),
                elementId, index + 1, count).stream();
          } else {
            return Stream.of(flowNodeInstanceDTO);
          }
    }).toList();
  }

  public BpmnTestEngine sendMessage(String messageName, VariablesDTO variables) {
    LOG.info("Sending message: " + messageName);
    DefinitionMessageEventTrigger messageEvent = new DefinitionMessageEventTrigger(messageName, variables);
    messageEventEmitter.send(messageEvent.toMessageEventKey(), messageEvent);
    return this;
  }

  public BpmnTestEngine waitForMessageSubscription(String messageName) {
    return waitForMessageSubscription(messageName, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitForMessageSubscription(String messageName, Duration duration) {
    Awaitility.await().atMost(duration).until(() -> messageSubscriptionMap.get(messageName), Objects::nonNull);
    return this;
  }

  public BpmnTestEngine waitForMessageSubscription(String receiveTaskMessage, String elementId,
      Set<String> correlationKeys) {
    return waitForMessageSubscription(receiveTaskMessage, elementId, correlationKeys,
        DEFAULT_DURATION
    );
  }

  public BpmnTestEngine waitForMessageSubscription(String messageName, String elementId,
      Set<String> correlationKeys, Duration duration) {
    Set<String> remainingCorrelationKeys = new HashSet<>(correlationKeys);
    Awaitility.await().atMost(duration).until(() -> {
      ConcurrentLinkedQueue<MessageEvent> messageEvents = messageSubscriptionMap.get(messageName);
      if (messageEvents == null) {
        return false;
      }
      MessageEvent poll;
      do {
        poll = messageEvents.poll();
        if (poll instanceof CorrelationMessageSubscription correlationMessageSubscription) {
            remainingCorrelationKeys.remove(correlationMessageSubscription.getCorrelationKey());
            return correlationMessageSubscription.getElementIdPath().contains(elementId) && remainingCorrelationKeys.isEmpty();
        }
      } while (poll != null);

      return false;
    }, found -> found);
    return this;
  }

  public BpmnTestEngine andSendMessageWithCorrelationKey(String messageName, String correlationKey,
      VariablesDTO variables) {
    LOG.info("Sending message: " + messageName);
    CorrelationMessageEventTrigger messageEvent = new CorrelationMessageEventTrigger(messageName, correlationKey, variables);
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

  public BpmnTestEngine waitUntilActivityHasState(String elementId, ActtivityStateEnum state, Duration duration) {
    Awaitility.await()
        .atMost(duration)
        .until(() -> {
          Map<UUID, Map<UUID, FlowNodeInstanceDTO>>flowNodeInstancesMap =
              flowNodeInstanceMap.get(activeProcessInstanceKey);
          ProcessInstanceDTO processInstanceDTO = processInstanceMap.get(activeProcessInstanceKey);
          Map<UUID, FlowNodeInstanceDTO> instanceMap = flowNodeInstancesMap.get(
              processInstanceDTO.getFlowNodeInstances().getFlowNodeInstancesId());
          return instanceMap.values().stream()
              .filter(i -> i instanceof ActivityInstanceDTO)
              .map(i -> (ActivityInstanceDTO)i)
              .anyMatch(a -> a.getElementId().equals(elementId) && a.getState() == state);
        }, Objects::nonNull);

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
    processInstanceTriggerEmitter.send(activeProcessInstanceKey, new TerminateTrigger(activeProcessInstanceKey, List.of(
        selectedFlowNodeInstance.getElementInstanceId())));
    return this;
  }
}
