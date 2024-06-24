package nl.qunit.bpmnmeister.engine.pi.testengine;


import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.engine.pd.MutableClock;
import nl.qunit.bpmnmeister.engine.pi.DebuggerUtil;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.CorrelationMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.CorrelationMessageSubscription;
import nl.qunit.bpmnmeister.pi.DefinitionMessageEventTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseResult;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.pi.state.MessageEventKey;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;
import org.xml.sax.SAXException;

@ApplicationScoped
public class BpmnTestEngine {

  private static final Logger LOG = Logger.getLogger(BpmnTestEngine.class);
  public static final Duration DEFAULT_DURATION;
  static {
    if (DebuggerUtil.isDebuggerAttached()) {
      DEFAULT_DURATION = Duration.ofSeconds(10);
    } else {
      DEFAULT_DURATION = Duration.ofSeconds(10);
    }
  }

  @Inject
  AdminClient adminClient;

  @Inject
  Clock clock;

  @Inject
  @Channel("process-instance-trigger-outgoing")
  Emitter<ProcessInstanceTrigger> triggerEmitter;

  @Inject
  @Channel("process-definition-xml-outgoing")
  Emitter<String> xmlEmitter;

  @Inject
  @Channel("process-instance-start-command-outgoing")
  Emitter<StartCommand> startCommandEmitter;

  @Inject
  @Channel("message-event-outgoing")
  Emitter<MessageEvent> messageEventEmitter;

  private final Map<ProcessInstanceKey, ConcurrentLinkedQueue<ProcessInstanceUpdate>> processInstanceQueueMap = new HashMap<>();
  private final Map<ProcessInstanceKey, List<ProcessInstanceKey>> processInstanceParentChildMap = new HashMap<>();
  private final Map<ProcessInstanceKey, ConcurrentLinkedQueue<ExternalTaskTrigger>> externalTaskTriggerQueueMap = new HashMap<>();
  private final Map<ProcessDefinitionKey, ConcurrentLinkedQueue<ProcessInstanceTrigger>> definitionToInstancesMap = new HashMap<>();
  private final Map<ProcessInstanceKey, ProcessInstanceUpdate> processInstanceMap = new HashMap<>();
  private final Map<String, ProcessDefinition> hashToDefinitionMap = new HashMap<>();
  private final Map<String, ConcurrentLinkedQueue<MessageEvent>> messageSubscriptionMap = new HashMap<>();
  private ProcessDefinition activeProcessDefintion;
  private ProcessInstanceUpdate activeProcessInstance;
  private ExternalTaskTrigger activeExternalTaskTrigger;
  private Definitions definitionsBeingDeployed;
  private MutableClock mutableClock;
  private ProcessInstanceUpdate latestInstantiatedProcessInstance;

  @PostConstruct
  public void init() {
    LOG.info("creating topics");
    adminClient.createTopics(
        Arrays.stream(Topics.values())
            .map(topic -> new NewTopic(topic.getTopicName(), 1, (short) 1))
            .toList());
    this.mutableClock = (MutableClock) clock;
  }

  @Incoming("process-instance-trigger-incoming")
  public void consume(ProcessInstanceTrigger trigger) {
    LOG.info("Received flow element trigger: " + trigger);
    if (trigger instanceof StartNewProcessInstanceTrigger startNewProcessInstanceTrigger) {
      ProcessDefinitionKey ProcessDefinitionKey = nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey.of(
          startNewProcessInstanceTrigger.getProcessDefinition());
      ConcurrentLinkedQueue<ProcessInstanceTrigger> processInstanceKeyList = definitionToInstancesMap.computeIfAbsent(
          ProcessDefinitionKey, k -> new ConcurrentLinkedQueue<>());
      processInstanceKeyList.add(trigger);
    }
  }

  @Incoming("message-event-incoming")
  public void consume(MessageEvent messageEvent) {
    LOG.info("Received message event: " + messageEvent);
    ConcurrentLinkedQueue<MessageEvent> messageEvents = messageSubscriptionMap.computeIfAbsent(
        messageEvent.getKey().messageName(), k -> new ConcurrentLinkedQueue<>());
    messageEvents.add(messageEvent);
  }

  @Incoming("external-task-trigger-incoming")
  public void consume(ExternalTaskTrigger externalTaskTrigger) {
    LOG.info("Received external task trigger: " + externalTaskTrigger);
    ConcurrentLinkedQueue<ExternalTaskTrigger> externalTaskTriggers = externalTaskTriggerQueueMap.computeIfAbsent(
        externalTaskTrigger.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    externalTaskTriggers.add(externalTaskTrigger);
  }

  @Incoming("process-instance-update-incoming")
  public void consume(ProcessInstanceUpdate processInstance) {
    LOG.info("Received process instance: " + processInstance);
    ConcurrentLinkedQueue<ProcessInstanceUpdate> processInstances1 = processInstanceQueueMap.computeIfAbsent(
        processInstance.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    processInstances1.add(processInstance);
    ProcessInstance previousProcessInstance = processInstanceMap.put(processInstance.getProcessInstanceKey(),
        processInstance);
    if (!processInstance.getParentInstanceKey().equals(ProcessInstanceKey.NONE)) {
      List<ProcessInstanceKey> processInstanceKeys = processInstanceParentChildMap.computeIfAbsent(
          processInstance.getParentInstanceKey(), k -> new ArrayList<>());
      processInstanceKeys.add(processInstance.getProcessInstanceKey());
    }
    if (previousProcessInstance == null) {
      latestInstantiatedProcessInstance = processInstance;
    }

  }

  @Incoming("process-definition-parsed-incoming")
  public void consume(ProcessDefinition processDefinition) {
    LOG.info("Received process definition: " + processDefinition);
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


  public void triggerExternalTaskResponse(ExternalTaskTrigger externalTaskTrigger,
      ExternalTaskResponseResult externalTaskResponseResult, Variables variables) {
      triggerEmitter.send(new ExternalTaskResponseTrigger(externalTaskTrigger.getProcessInstanceKey(),
          externalTaskTrigger.getElementId(), externalTaskResponseResult, variables));
  }


  public BpmnTestEngine deployProcessDefinition(String filename)
      throws JAXBException, NoSuchAlgorithmException, IOException, ParserConfigurationException, SAXException {
    LOG.info("Deploying process definition: " + filename);
    String xml = IOUtils.toString(BpmnTestEngine.class.getResourceAsStream(filename));
    definitionsBeingDeployed = new BpmnParser().parse(xml);
    xmlEmitter.send(KafkaRecord.of(filename, xml));
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

  public BpmnTestEngine startProcessInstance(Variables variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);
    StartCommand startCommand = new StartCommand(
        new ProcessInstanceKey(UUID.randomUUID()),
        ProcessInstanceKey.NONE,
        Constants.NONE,
        Constants.NONE,
        activeProcessDefintion.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
        variables);
    startCommandEmitter.send(KafkaRecord.of(processDefinitionKey.getProcessDefinitionId(), startCommand));

    ProcessInstanceKey activeProcessInstanceKey;
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

  public BpmnTestEngine waitUntilServiceTaskIsWaitingForResponse(String elementId) {
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
          List<ProcessInstanceKey> childKeys = processInstanceParentChildMap.get(activeProcessInstance.getProcessInstanceKey());
          if (childKeys == null) {
            return null;
          }
          if (childKeys.size() >= expectedCount) {
            ProcessInstanceKey processInstanceKey = childKeys.get(childKeys.size() - 1);
            ProcessInstanceUpdate processInstance = processInstanceMap.get(processInstanceKey);
            return processInstance != null && processInstance.getProcessInstanceState() == processInstanceState ? processInstance : null;
          }
          return null;
        }, Objects::nonNull);
    return this;
  }

  public BpmnTestEngine andRespondWithSuccess(Variables of) {
    triggerExternalTaskResponse(activeExternalTaskTrigger, new ExternalTaskResponseResult(true, null, null), of);
    return this;
  }
  public BpmnTestEngine andRespondWithFailure(boolean allowRetry, String errorMessage, Variables of) {
    triggerExternalTaskResponse(activeExternalTaskTrigger, new ExternalTaskResponseResult(false, allowRetry, errorMessage), of);
    return this;
  }

  public BpmnTestEngine   waitUntilCompleted() {
    return waitUntilCompleted(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilCompleted(Duration duration) {
    try {
      activeProcessInstance = Awaitility.await()
          .atMost(duration)
          .until(() -> {
            if (activeProcessInstance != null && activeProcessInstance.getProcessInstanceState()
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
                  && poll.getProcessInstanceState().isFinished()) {
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
    ProcessInstanceUpdate parentProcessInstance = processInstanceMap.get(activeProcessInstance.getParentInstanceKey());
    activeProcessInstance = parentProcessInstance;
    return this;
  }

  public ProcessInstanceAssert assertThatParentProcess() {
    ProcessInstanceUpdate parentProcessInstance = processInstanceMap.get(activeProcessInstance.getParentInstanceKey());
    activeProcessInstance = parentProcessInstance;
    return new ProcessInstanceAssert(parentProcessInstance, this);
  }

  public ProcessDefinition deployedProcessDefinition() {
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
    triggerEmitter.send(new TerminateTrigger(activeProcessInstance.getProcessInstanceKey(), Constants.NONE));
    return this;
  }
  public BpmnTestEngine terminateChildProcessesForElement(String elementId) {
    triggerEmitter.send(new TerminateTrigger(activeProcessInstance.getProcessInstanceKey(), elementId));
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
    Optional<FlowNodeState> flowNodeState = poll.getFlowNodeStates().get(elementId);
    return flowNodeState.map(FlowNodeState::getPassedCnt).orElse(0);
  }

  public BpmnTestEngine sendMessage(String messageName, Variables variables) {
    LOG.info("Sending message: " + messageName);
    DefinitionMessageEventTrigger messageEvent = new DefinitionMessageEventTrigger(messageName, variables);
    messageEventEmitter.send(KafkaRecord.of(new MessageEventKey(messageEvent.getMessageName()), messageEvent));
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
      Variables variables) {
    LOG.info("Sending message: " + messageName);
    CorrelationMessageEventTrigger messageEvent = new CorrelationMessageEventTrigger(messageName, correlationKey, variables);
    messageEventEmitter.send(KafkaRecord.of(new MessageEventKey(messageEvent.getMessageName()), messageEvent));
    return this;

  }

  public ExternalTaskAssert assertThatExternalTask() {
    return new ExternalTaskAssert(activeExternalTaskTrigger, this);
  }

  public ProcessInstanceUpdate getProcessInstance(ProcessInstanceKey parentInstanceKey) {
    return processInstanceMap.get(parentInstanceKey);
  }

  public BpmnTestEngine waitUntilElementIsActive(String elementId, Duration duration) {

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
                 poll.getFlowNodeStates().get(elementId).isPresent() &&
                FlowNodeStateEnum.ACTIVE == poll.getFlowNodeStates().get(elementId).get().getState()) {
              return poll;
            }
          } while (poll != null);
          return null;
        }, Objects::nonNull);
    return this;  }

  public BpmnTestEngine waitUntilElementIsActive(String elementId) {
    return waitUntilElementIsActive(elementId, DEFAULT_DURATION);
  }
}
