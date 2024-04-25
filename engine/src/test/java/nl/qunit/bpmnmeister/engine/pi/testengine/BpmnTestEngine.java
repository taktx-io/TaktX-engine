package nl.qunit.bpmnmeister.engine.pi.testengine;


import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseResult;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceStartCommand;
import nl.qunit.bpmnmeister.pi.Trigger;
import nl.qunit.bpmnmeister.pi.Variables;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BpmnTestEngine {

  private static final Logger LOG = Logger.getLogger(BpmnTestEngine.class);
  public static final Duration DEFAULT_DURATION = Duration.ofSeconds(10);
  private static final long DEFAULT_INTERVAL_MILLIS = 1000;

  @Inject
  AdminClient adminClient;

  @Inject
  @Channel("process-instance-trigger-outgoing")
  Emitter<Trigger> triggerEmitter;

  @Inject
  @Channel("process-definition-xml-outgoing")
  Emitter<String> xmlEmitter;

  @Inject
  @Channel("process-instance-start-command-outgoing")
  Emitter<ProcessInstanceStartCommand> startCommandEmitter;

  private final Map<ProcessInstanceKey, ConcurrentLinkedQueue<ProcessInstance>> processInstanceQueueMap = new HashMap<>();
  private final Map<ProcessInstanceKey, ConcurrentLinkedQueue<ExternalTaskTrigger>> externalTaskTriggerQueueMap = new HashMap<>();
  private final Map<ProcessDefinitionKey, ConcurrentLinkedQueue<Trigger>> definitionToInstancesMap = new HashMap<>();
  private final Map<ProcessInstanceKey, ProcessInstance> processInstanceMap = new HashMap<>();
  private final Map<String, ProcessDefinition> hashToDefinitionMap = new HashMap<>();
  private ProcessDefinition activeProcessDefintion;
  private ProcessInstance activeProcessInstance;
  private ExternalTaskTrigger activeExternalTaskTrigger;
  private Definitions definitionsBeingDeployed;


  @PostConstruct
  public void init() {
    adminClient.createTopics(
        Arrays.stream(Topics.values())
            .map(topic -> new NewTopic(topic.getTopicName(), 1, (short) 1))
            .toList());
  }

  @Incoming("process-instance-trigger-incoming")
  public void consume(Trigger trigger) {
    LOG.info("Received flow element trigger: " + trigger);
    if (!trigger.getProcessDefinition().equals(ProcessDefinition.NONE)) {
      ProcessDefinitionKey ProcessDefinitionKey = nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey.of(
          trigger.getProcessDefinition());
      ConcurrentLinkedQueue<Trigger> processInstanceKeyList = definitionToInstancesMap.computeIfAbsent(
          ProcessDefinitionKey, k -> new ConcurrentLinkedQueue<>());
      processInstanceKeyList.add(trigger);
    }
  }

  @Incoming("external-task-trigger-incoming")
  public void consume(ExternalTaskTrigger externalTaskTrigger) {
    LOG.info("Received external task trigger: " + externalTaskTrigger);
    ConcurrentLinkedQueue<ExternalTaskTrigger> externalTaskTriggers = externalTaskTriggerQueueMap.computeIfAbsent(
        externalTaskTrigger.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    externalTaskTriggers.add(externalTaskTrigger);
  }

  @Incoming("process-instance-incoming")
  public void consume(ProcessInstance processInstance) {
    LOG.info("Received process instance: " + processInstance);
    ConcurrentLinkedQueue<ProcessInstance> processInstances1 = processInstanceQueueMap.computeIfAbsent(
        processInstance.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    processInstances1.add(processInstance);
    processInstanceMap.put(processInstance.getProcessInstanceKey(), processInstance);
  }

  @Incoming("process-definition-parsed-incoming")
  public void consume(ProcessDefinition processDefinition) {
    LOG.info("Received process definition: " + processDefinition);
    hashToDefinitionMap.put(processDefinition.getDefinitions().getDefinitionsKey().getHash(), processDefinition);
  }

  public BpmnTestEngine triggerNewProcessInstance(String elementId) {
    ProcessInstanceKey processInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
    Trigger trigger = new FlowElementTrigger(processInstanceKey, ProcessInstanceKey.NONE,
        activeProcessDefintion, new String(elementId), Constants.NONE, Variables.EMPTY);
    triggerEmitter.send(trigger);
    return this;
  }

  public ExternalTaskTrigger pollExternalTask(String elementId) {
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
      throws JAXBException, NoSuchAlgorithmException, IOException {
    LOG.info("Deploying process definition: " + filename);
    String xml = IOUtils.toString(BpmnTestEngine.class.getResourceAsStream(filename));
    definitionsBeingDeployed = BpmnParser.parse(xml);
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
      throws JAXBException, NoSuchAlgorithmException, IOException {
    deployProcessDefinition(filename);
    waitForProcessDeployment();
    return this;
  }

  public BpmnTestEngine startProcessInstance(Variables variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);
    String startEventId = activeProcessDefintion.getDefinitions().getRootProcess().getFlowElements()
        .getStartEvents().get(0).getId();
    ProcessInstanceStartCommand startCommand = new ProcessInstanceStartCommand(
        activeProcessDefintion.getDefinitions().getDefinitionsKey().getProcessDefinitionId(), startEventId, variables);
    startCommandEmitter.send(KafkaRecord.of(processDefinitionKey.getProcessDefinitionId(), startCommand));

    ProcessInstanceKey activeProcessInstanceKey = Awaitility.await()
        .until(() -> {
          ConcurrentLinkedQueue<Trigger> flowElementTriggers = definitionToInstancesMap.get(
              processDefinitionKey);
          if (flowElementTriggers == null || flowElementTriggers.isEmpty()) {
            return null;
          }
          Trigger poll = flowElementTriggers.poll();
          if (poll != null && poll.getProcessDefinition() != null && ProcessDefinitionKey.of(poll.getProcessDefinition()).equals(processDefinitionKey)) {
            return poll.getProcessInstanceKey();
          }
          return null;
        }, Objects::nonNull);

    activeProcessInstance = Awaitility.await()
        .until(() -> {
          ConcurrentLinkedQueue<ProcessInstance> processInstances = processInstanceQueueMap.get(
              activeProcessInstanceKey);
          if (processInstances == null || processInstances.isEmpty()) {
            return null;
          }
          ProcessInstance poll = processInstances.poll();
          if (poll != null && poll.getProcessInstanceKey() != null && poll.getProcessInstanceKey()
              .equals(activeProcessInstanceKey)) {
            return poll;
          }
          return null;
        }, Objects::nonNull);

    return this;
  }

  public BpmnTestEngine waitUntilServiceTaskIsWaitingForResponse(String elementId) {
    activeExternalTaskTrigger = Awaitility.await().atMost(DEFAULT_DURATION)
        .until(() -> pollExternalTask(elementId),
            externalTaskTrigger -> externalTaskTrigger != null && externalTaskTrigger.getElementId()
                .equals(elementId));
    return this;
  }


  public BpmnTestEngine waitUntilChildProcessIsStarted() {
    activeProcessInstance = Awaitility.await().atMost(DEFAULT_DURATION)
        .until(() -> {
          Optional<ProcessInstanceKey> first = processInstanceQueueMap.keySet().stream()
              .filter(k -> k.getParentProcessInstanceId().equals(activeProcessInstance.getProcessInstanceKey()))
              .findFirst();
          if (first.isEmpty()) {
            return null;
          }
          ConcurrentLinkedQueue<ProcessInstance> processInstances = processInstanceQueueMap.get(first.get());
          if (processInstances == null || processInstances.isEmpty()) {
            return null;
          }
          ProcessInstance poll = processInstances.poll();
          if (poll != null && poll.getProcessInstanceKey() != null && poll.getProcessInstanceState().isStarted()) {
            return poll;
          }
          return null;
        }, Objects::nonNull);
    return this;
  }

  public BpmnTestEngine andRespondWithSuccess(Variables of) {
    triggerExternalTaskResponse(activeExternalTaskTrigger, new ExternalTaskResponseResult(true, ""), of);
    return this;
  }

  public BpmnTestEngine waitUntilCompleted() {
    return waitUntilCompleted(DEFAULT_DURATION);
  }

  public BpmnTestEngine waitUntilCompleted(Duration duration) {

    activeProcessInstance = Awaitility.await()
        .atMost(duration)
        .until(() -> {
          ConcurrentLinkedQueue<ProcessInstance> processInstances = processInstanceQueueMap.get(
              activeProcessInstance.getProcessInstanceKey());
          if (processInstances == null || processInstances.isEmpty()) {
            return null;
          }
          ProcessInstance poll = null;
          do {
            poll = processInstances.poll();
            if (poll != null && poll.getProcessInstanceKey() != null && poll.getProcessInstanceKey()
                .equals(activeProcessInstance.getProcessInstanceKey()) && poll.getProcessInstanceState().isFinished()) {
              return poll;
            }
          } while (poll != null);
          return null;
        }, Objects::nonNull);
    return this;
  }

  public ProcessInstanceAssert assertThatProcess() {
    return new ProcessInstanceAssert(activeProcessInstance, this);
  }

  public void clear() {
    processInstanceQueueMap.clear();
    externalTaskTriggerQueueMap.clear();
    definitionToInstancesMap.clear();
    processInstanceMap.clear();
    activeProcessDefintion = null;
    activeProcessInstance = null;
    activeExternalTaskTrigger = null;
  }

  public ProcessInstanceAssert assertThatParentProcess() {
    ProcessInstance parentProcessInstance = processInstanceMap.get(
        activeProcessInstance.getParentProcessInstanceKey());
    activeProcessInstance = parentProcessInstance;
    return new ProcessInstanceAssert(parentProcessInstance, this);
  }

  public ProcessDefinition deployedProcessDefinition() {
    return activeProcessDefintion;
  }
}
