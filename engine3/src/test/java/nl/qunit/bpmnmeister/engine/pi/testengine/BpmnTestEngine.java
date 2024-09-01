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
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.engine.generic.TopologyProducer;
import nl.qunit.bpmnmeister.engine.pd.MutableClock;
import nl.qunit.bpmnmeister.engine.pi.DebuggerUtil;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.CorrelationMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.DefinitionMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseResult2;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger2;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger2;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger2;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;
import org.xml.sax.SAXException;

public class BpmnTestEngine implements KafkaConsumerRebalanceListener {

  private static final Logger LOG = Logger.getLogger(BpmnTestEngine.class);
  public static final Duration DEFAULT_DURATION;
  static {
    if (DebuggerUtil.isDebuggerAttached()) {
      DEFAULT_DURATION = Duration.ofSeconds(10);
    } else {
      DEFAULT_DURATION = Duration.ofSeconds(10);
    }
  }

  private final Map<UUID, ConcurrentLinkedQueue<ProcessInstanceUpdate>> processInstanceQueueMap = new HashMap<>();
  private final Map<UUID, Set<UUID>> processInstanceParentChildMap = new HashMap<>();
  private final Map<UUID, ConcurrentLinkedQueue<ExternalTaskTrigger>> externalTaskTriggerQueueMap = new HashMap<>();
  private final Map<ProcessDefinitionKey, ConcurrentLinkedQueue<ProcessInstanceTrigger2>> definitionToInstancesMap = new HashMap<>();
  private final Map<UUID, ProcessInstanceUpdate> processInstanceMap = new HashMap<>();
  private final Map<String, ProcessDefinitionDTO> hashToDefinitionMap = new HashMap<>();
  private final Map<String, ConcurrentLinkedQueue<MessageEvent>> messageSubscriptionMap = new HashMap<>();
  private ProcessDefinitionDTO activeProcessDefintion;
  private ProcessInstanceUpdate activeProcessInstance;
  private ExternalTaskTrigger activeExternalTaskTrigger;
  private DefinitionsDTO definitionsBeingDeployed;
  private final MutableClock mutableClock;
  private ProcessInstanceUpdate latestInstantiatedProcessInstance;
  private KafkaConsumerUtil<UUID, ProcessInstanceTrigger2> processInstanceTriggerConsumer;
  private KafkaConsumerUtil<MessageEventKey, MessageEvent> messageEventConsumer;
  private KafkaConsumerUtil<UUID, ExternalTaskTrigger> externalTaskTriggerConsumer;
  private KafkaConsumerUtil<UUID, ProcessInstanceUpdate> processInstanceUpdateConsumer;
  private KafkaConsumerUtil<ProcessDefinitionKey, ProcessDefinitionDTO> processDefinitionParsedConsumer;
  private KafkaProducerUtil<UUID, ProcessInstanceTrigger2> triggerEmitter;
  private KafkaProducerUtil<String, String> xmlEmitter;
  private KafkaProducerUtil<String, StartCommand>  startCommandEmitter;
  private KafkaProducerUtil<MessageEventKey, MessageEvent> messageEventEmitter;

  public BpmnTestEngine(Clock clock) {
    this.mutableClock = (MutableClock) clock;
  }

  public void init() {
    String kafkaBootstrapServers = ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    AdminClient adminClient = AdminClient.create(
        Map.of("bootstrap.servers", kafkaBootstrapServers));
    adminClient.createTopics(
        Arrays.stream(Topics.values())
            .map(topic -> new NewTopic(topic.getTopicName(), 5, (short) 1))
            .toList());

    triggerEmitter = new KafkaProducerUtil(Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
        TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.serializer().getClass().getName(),
        ObjectMapperSerializer.class.getName());
    xmlEmitter = new KafkaProducerUtil<>(Topics.XML_TOPIC.getTopicName(),
        StringSerializer.class.getName(),
        StringSerializer.class.getName());
    startCommandEmitter = new KafkaProducerUtil<>(Topics.DEFINITIONS_TOPIC.getTopicName(),
        StringSerializer.class.getName(),
        ObjectMapperSerializer.class.getName());
    messageEventEmitter = new KafkaProducerUtil<>(Topics.MESSAGE_EVENT_TOPIC.getTopicName(),
        ObjectMapperSerializer.class.getName(),
        ObjectMapperSerializer.class.getName());
    processInstanceTriggerConsumer = new KafkaConsumerUtil<>("test-group",
        Topics.PROCESS_INSTANCE_TRIGGER_TOPIC,
        TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
        ProcessInstanceTriggerDeserializer.class.getName(),
        this::consume);
    messageEventConsumer = new KafkaConsumerUtil<>("test-group",
        Topics.MESSAGE_EVENT_TOPIC,
        MessageEventKeyDeserializer.class.getName(),
        MessageEventDeserializer.class.getName(),
        this::consume);
    externalTaskTriggerConsumer = new KafkaConsumerUtil<>("test-group",
        Topics.EXTERNAL_TASK_TRIGGER_TOPIC,
        TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
        ExternalTaskTriggerDeserializer.class.getName(),
        this::consume);
    processInstanceUpdateConsumer = new KafkaConsumerUtil<>("test-group",
        Topics.PROCESS_INSTANCE_UPDATE_TOPIC,
        TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer().getClass().getName(),
        ProcessInstanceUpdateDeserializer.class.getName(),
        this::consume);
    processDefinitionParsedConsumer = new KafkaConsumerUtil<>("test-group",
        Topics.PROCESS_DEFINITION_PARSED_TOPIC,
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
    processInstanceUpdateConsumer.stop();
    processDefinitionParsedConsumer.stop();
    messageEventEmitter.close();
    xmlEmitter.close();
    startCommandEmitter.close();
    triggerEmitter.close();

  }

  public void consume(ProcessInstanceTrigger2 trigger) {
    LOG.info("Received flow element trigger: " + trigger);
    if (trigger instanceof StartNewProcessInstanceTrigger2 startNewProcessInstanceTrigger) {
      ProcessDefinitionKey ProcessDefinitionKey = nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey.of(
          startNewProcessInstanceTrigger.getProcessDefinition());
      ConcurrentLinkedQueue<ProcessInstanceTrigger2> processInstanceKeyList = definitionToInstancesMap.computeIfAbsent(
          ProcessDefinitionKey, k -> new ConcurrentLinkedQueue<>());
      processInstanceKeyList.add(trigger);
    }
  }

  public void consume(MessageEvent messageEvent) {
    LOG.info("Received message event: " + messageEvent);
    ConcurrentLinkedQueue<MessageEvent> messageEvents = messageSubscriptionMap.computeIfAbsent(
        messageEvent.getKey().messageName(), k -> new ConcurrentLinkedQueue<>());
    messageEvents.add(messageEvent);
  }

  public void consume(ExternalTaskTrigger externalTaskTrigger) {
    LOG.info("Received external task trigger: " + externalTaskTrigger);
    ConcurrentLinkedQueue<ExternalTaskTrigger> externalTaskTriggers = externalTaskTriggerQueueMap.computeIfAbsent(
        externalTaskTrigger.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    externalTaskTriggers.add(externalTaskTrigger);
  }

  public void consume(ProcessInstanceUpdate processInstance) {
    LOG.info("Received process instance: " + processInstance);
    ConcurrentLinkedQueue<ProcessInstanceUpdate> processInstances1 = processInstanceQueueMap.computeIfAbsent(
        processInstance.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    processInstances1.add(processInstance);
    ProcessInstanceDTO previousProcessInstance = processInstanceMap.put(processInstance.getProcessInstanceKey(),
        processInstance);
    if (previousProcessInstance == null) {
      latestInstantiatedProcessInstance = processInstance;
    }

  }

  public void consume(ProcessDefinitionDTO processDefinition) {
    LOG.info("Received process definition: " + processDefinition + " " + processDefinition.getDefinitions().getDefinitionsKey().getHash()) ;
    hashToDefinitionMap.put(processDefinition.getDefinitions().getDefinitionsKey().getHash(), processDefinition);
  }

  public ExternalTaskTrigger pollExternalTask() {
    ConcurrentLinkedQueue<ExternalTaskTrigger> externalTaskTriggers = externalTaskTriggerQueueMap.get(activeProcessInstance.getProcessInstanceKey());
    if (externalTaskTriggers == null) {
      return null;
    }
    ExternalTaskTrigger externalTaskTrigger = externalTaskTriggers.poll();
    return externalTaskTrigger;
  }


  public void triggerExternalTaskResponse(UUID rootInstanceKey, ExternalTaskTrigger externalTaskTrigger,
      ExternalTaskResponseResult2 externalTaskResponseResult, VariablesDTO variables) {
      triggerEmitter.send(rootInstanceKey,
          new ExternalTaskResponseTrigger2(externalTaskTrigger.getProcessInstanceKey(),
          externalTaskTrigger.getElementId(), externalTaskTrigger.getElementInstanceId(), externalTaskResponseResult, variables)
      );
  }


  public BpmnTestEngine deployProcessDefinition(String filename)
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    LOG.info("Deploying process definition: " + filename);
    String xml = IOUtils.toString(BpmnTestEngine.class.getResourceAsStream(filename));
    definitionsBeingDeployed = new BpmnParser().parse(xml);
    xmlEmitter.send(filename, xml);
    return this;
  }

  public BpmnTestEngine waitForProcessDeployment() {
    activeProcessDefintion = Awaitility.await()
        .until(() -> {
          return hashToDefinitionMap.get(definitionsBeingDeployed.getDefinitionsKey().getHash());
        }, Objects::nonNull);

    return this;
  }

  public BpmnTestEngine deployProcessDefinitionAndWait(String filename)
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    deployProcessDefinition(filename);
    waitForProcessDeployment();
    return this;
  }

  public BpmnTestEngine startProcessInstance(VariablesDTO variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);
    StartCommand startCommand = new StartCommand(
        Constants.NONE_UUID,
        Constants.NONE,
        Constants.NONE,
        Constants.NONE_UUID,
        activeProcessDefintion.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
        variables);
    startCommandEmitter.send(processDefinitionKey.getProcessDefinitionId(), startCommand);

    UUID activeProcessInstanceKey;
    try {
       activeProcessInstanceKey = Awaitility.await()
          .until(() -> {
            ConcurrentLinkedQueue<ProcessInstanceTrigger2> flowElementTriggers = definitionToInstancesMap.get(
                processDefinitionKey);
            if (flowElementTriggers == null || flowElementTriggers.isEmpty()) {
              return null;
            }
            ProcessInstanceTrigger2 poll = flowElementTriggers.poll();
            if (poll instanceof StartNewProcessInstanceTrigger2 startNewProcessInstanceTrigger &&
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
      activeProcessInstance = Awaitility.await()
          .until(() -> {
            ConcurrentLinkedQueue<ProcessInstanceUpdate> processInstances = processInstanceQueueMap.get(
                activeProcessInstanceKey);
            if (processInstances == null || processInstances.isEmpty()) {
              return null;
            }
            ProcessInstanceUpdate poll = processInstances.poll();
            if (poll != null && poll.getProcessInstanceKey() != null && poll.getProcessInstanceKey()
                .equals(activeProcessInstanceKey)) {
              return poll;
            }
            return null;
          }, Objects::nonNull);
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
                                   externalTaskTrigger.getElementId().equals(elementId));
    return this;
  }


  public BpmnTestEngine waitForNewProcessInstance() {
    ProcessInstanceUpdate referenceProcessInstance = latestInstantiatedProcessInstance;
    activeProcessInstance = Awaitility.await().atMost(DEFAULT_DURATION)
        .until(() -> latestInstantiatedProcessInstance,
            instance -> !Objects.equals(referenceProcessInstance, instance));
    return this;
  }

  public BpmnTestEngine waitUntilChildProcessIsStarted() {
    return waitUntilChildProcessesHaveState(1, ProcessInstanceState.ACTIVE);
  }
 public BpmnTestEngine waitUntilChildProcessIsTerminated() {
    return waitUntilChildProcessesHaveState(1, ProcessInstanceState.TERMINATED);
  }

  public BpmnTestEngine waitUntilChildProcessesHaveState(int expectedCount, ProcessInstanceState processInstanceState) {
    activeProcessInstance = Awaitility.await().atMost(DEFAULT_DURATION)
        .until(() -> {
          Set<UUID> childKeys = processInstanceParentChildMap.get(activeProcessInstance.getProcessInstanceKey());
          if (childKeys == null) {
            return null;
          }
          if (childKeys.size() >= expectedCount) {
            UUID processInstanceKey = childKeys.iterator().next();
            ProcessInstanceUpdate processInstance = processInstanceMap.get(processInstanceKey);
            return processInstance != null && processInstance.getFlowNodeStates().getState() == processInstanceState ? processInstance : null;
          }
          return null;
        }, Objects::nonNull);
    return this;
  }

  public BpmnTestEngine andRespondWithSuccess(VariablesDTO of) {
    triggerExternalTaskResponse(activeProcessInstance.getProcessInstanceKey(), activeExternalTaskTrigger, new ExternalTaskResponseResult2(true, null, null), of);
    return this;
  }
  public BpmnTestEngine andRespondWithFailure(boolean allowRetry, String errorMessage, VariablesDTO of) {
    triggerExternalTaskResponse(activeProcessInstance.getProcessInstanceKey(),
        activeExternalTaskTrigger, new ExternalTaskResponseResult2(false, allowRetry, errorMessage), of);
    return this;
  }

  public BpmnTestEngine waitUntilCompleted() {
    return waitUntilCompleted(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilCompleted(Duration duration) {
    try {
      activeProcessInstance = Awaitility.await()
          .atMost(duration)
          .until(() -> {
            if (activeProcessInstance != null && activeProcessInstance.getFlowNodeStates().getState()
                .isFinished()) {
              return activeProcessInstance;
            }
            ConcurrentLinkedQueue<ProcessInstanceUpdate> processInstances = processInstanceQueueMap.get(
                activeProcessInstance.getProcessInstanceKey());
            if (processInstances == null || processInstances.isEmpty()) {
              return null;
            }
            ProcessInstanceUpdate poll = null;
            do {
              poll = processInstances.poll();
              if (poll != null && poll.getProcessInstanceKey() != null
                  && poll.getProcessInstanceKey()
                      .equals(activeProcessInstance.getProcessInstanceKey())
                  && poll.getFlowNodeStates().getState().isFinished()) {
                return poll;
              }
            } while (poll != null);
            return null;
          }, Objects::nonNull);
    } catch (ConditionTimeoutException e) {
      LOG.error("Process instance: " + activeProcessInstance + " did not complete in time");
      LOG.error(processInstanceMap);
      throw e;
    }
    return this;
  }

  public ProcessInstanceAssert assertThatProcess() {
    return new ProcessInstanceAssert(activeProcessInstance, this);
  }

  public void clear() {
    processInstanceQueueMap.clear();
    processInstanceParentChildMap.clear();
    externalTaskTriggerQueueMap.clear();
    definitionToInstancesMap.clear();
    processInstanceMap.clear();
    hashToDefinitionMap.clear();
    messageSubscriptionMap.clear();
    activeProcessDefintion = null;
    activeProcessInstance = null;
    activeExternalTaskTrigger = null;
  }

  public BpmnTestEngine parentProcess() {
//    ProcessInstanceUpdate parentProcessInstance = processInstanceMap.get(activeProcessInstance.getParentInstanceKey());
//    activeProcessInstance = parentProcessInstance;
    return this;
  }

  public ProcessInstanceAssert assertThatParentProcess() {
//    ProcessInstanceUpdate parentProcessInstance = processInstanceMap.get(activeProcessInstance.getParentInstanceKey());
//    activeProcessInstance = parentProcessInstance;
//    return new ProcessInstanceAssert(parentProcessInstance, this);
    return new ProcessInstanceAssert(activeProcessInstance, this);
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
    Assertions.assertThrows(ConditionTimeoutException.class, () ->
      waitForNewProcessInstance());
    return this;
  }

  public BpmnTestEngine terminateProcessWithChildProcesses() {
    triggerEmitter.send(activeProcessInstance.getProcessInstanceKey(), new TerminateTrigger(activeProcessInstance.getProcessInstanceKey(), Constants.NONE, Constants.NONE_UUID));
    return this;
  }
  public BpmnTestEngine terminateChildProcessesForElement(String elementId) {
    triggerEmitter.send(activeProcessInstance.getProcessInstanceKey(), new TerminateTrigger(activeProcessInstance.getProcessInstanceKey(), elementId, Constants.NONE_UUID));
    return this;
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId) {
    return waitUntilElementHasPassed(elementId, 1, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId, int count) {
    return waitUntilElementHasPassed(elementId, count, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasPassed(String elementId, int count, Duration duration) {

    activeProcessInstance = Awaitility.await()
        .atMost(duration)
        .until(() -> {
          ConcurrentLinkedQueue<ProcessInstanceUpdate> processInstances = processInstanceQueueMap.get(
              activeProcessInstance.getProcessInstanceKey());
          LOG.info("Pi for " + activeProcessInstance.getProcessInstanceKey() + " in : " + processInstanceQueueMap);
          if (processInstances == null || processInstances.isEmpty()) {
            return null;
          }
          ProcessInstanceUpdate poll = null;
          do {
            poll = processInstances.poll();
            LOG.info("Poll: " + poll);
            if (poll != null && poll.getProcessInstanceKey() != null && poll.getProcessInstanceKey()
                .equals(activeProcessInstance.getProcessInstanceKey()) &&
                getPassedCnt(elementId, poll) >= count) {
              return poll;
            }
          } while (poll != null);
          return null;
        }, Objects::nonNull);
    return this;
  }

  private static int getPassedCnt(String elementId, ProcessInstanceUpdate poll) {
    List<FlowNodeStateDTO> flowNodeState = poll.getFlowNodeStates().get(elementId).stream().filter(e -> e.getPassedCnt() > 0).toList();

    return flowNodeState.size();
  }

  public BpmnTestEngine sendMessage(String messageName, VariablesDTO variables) {
    LOG.info("Sending message: " + messageName);
    DefinitionMessageEventTrigger messageEvent = new DefinitionMessageEventTrigger(messageName, variables);
    messageEventEmitter.send(new MessageEventKey(messageEvent.getMessageName()), messageEvent);
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
      MessageEvent poll = null;
      do {
        poll = messageEvents.poll();
        if (poll instanceof CorrelationMessageSubscription correlationMessageSubscription) {
            remainingCorrelationKeys.remove(correlationMessageSubscription.getCorrelationKey());
            return correlationMessageSubscription.getElementId().equals(elementId) && remainingCorrelationKeys.isEmpty();
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
    messageEventEmitter.send(new MessageEventKey(messageEvent.getMessageName()), messageEvent);
    return this;

  }

  public ExternalTaskAssert assertThatExternalTask() {
    return new ExternalTaskAssert(activeExternalTaskTrigger, this);
  }

  public ProcessInstanceUpdate getProcessInstance(UUID parentInstanceKey) {
    return processInstanceMap.get(parentInstanceKey);
  }

  public BpmnTestEngine waitUntilElementHasState(String elementId, FlowNodeStateEnum state, Duration duration) {

    activeProcessInstance = Awaitility.await()
        .atMost(duration)
        .until(() -> {
          ConcurrentLinkedQueue<ProcessInstanceUpdate> processInstances = processInstanceQueueMap.get(
              activeProcessInstance.getProcessInstanceKey());
          if (processInstances == null || processInstances.isEmpty()) {
            return null;
          }
          ProcessInstanceUpdate poll = null;
          do {
            poll = processInstances.poll();
            if (poll != null &&
                poll.getProcessInstanceKey() != null &&
                poll.getProcessInstanceKey().equals(activeProcessInstance.getProcessInstanceKey()) &&
                 !poll.getFlowNodeStates().get(elementId).isEmpty() &&
                state == poll.getFlowNodeStates().get(elementId).get(0).getState()) {
              return poll;
            }
          } while (poll != null);
          return null;
        }, Objects::nonNull);
    return this;  }

  public BpmnTestEngine waitUntilElementIsActive(String elementId, Duration duration) {
    return waitUntilElementHasState(elementId, FlowNodeStateEnum.WAITING, duration);
  }

  public BpmnTestEngine waitUntilElementIsActive(String elementId) {
    return waitUntilElementIsActive(elementId, DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilElementHasFailed(String elementId) {
    return waitUntilElementHasState(elementId, FlowNodeStateEnum.FAILED, DEFAULT_DURATION);
  }
}
