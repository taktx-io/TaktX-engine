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
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
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
import nl.qunit.bpmnmeister.util.GenerationExtractor;
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

  private final Map<ProcessInstanceKey, ConcurrentLinkedQueue<ProcessInstance>> processInstanceMap = new HashMap<>();
  private final Map<ProcessInstanceKey, ConcurrentLinkedQueue<ExternalTaskTrigger>> externalTaskTriggerMap = new HashMap<>();
  private final Map<ProcessDefinitionKey, ProcessDefinition> processDefinitionMap  = new HashMap<>();
  private final Map<ProcessDefinitionKey, ConcurrentLinkedQueue<FlowElementTrigger>> definitionToInstancesMap = new HashMap<>();
  private ProcessDefinition activeProcessDefintion;
  private ProcessInstanceKey activeProcessInstanceKey;
  private ProcessInstance activeProcessInstance;
  private ExternalTaskTrigger activeExternalTaskTrigger;


  @PostConstruct
  public void init() {
    adminClient.createTopics(
        Arrays.stream(Topics.values())
            .map(topic -> new NewTopic(topic.getTopicName(), 1, (short) 1))
            .toList());
  }

  @Incoming("process-instance-trigger-incoming")
  public void consume(FlowElementTrigger flowElementTrigger) {
    LOG.info("Received flow element trigger: " + flowElementTrigger);
    if (!flowElementTrigger.getProcessDefinition().equals(ProcessDefinition.NONE)) {
      ProcessDefinitionKey ProcessDefinitionKey = nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey.of(
          flowElementTrigger.getProcessDefinition());
      ConcurrentLinkedQueue<FlowElementTrigger> processInstanceKeyList = definitionToInstancesMap.computeIfAbsent(
          ProcessDefinitionKey, k -> new ConcurrentLinkedQueue<>());
      processInstanceKeyList.add(flowElementTrigger);
    }
  }

  @Incoming("external-task-trigger-incoming")
  public void consume(ExternalTaskTrigger externalTaskTrigger) {
    LOG.info("Received external task trigger: " + externalTaskTrigger);
    ConcurrentLinkedQueue<ExternalTaskTrigger> externalTaskTriggers = externalTaskTriggerMap.computeIfAbsent(
        externalTaskTrigger.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    externalTaskTriggers.add(externalTaskTrigger);
  }

  @Incoming("process-instance-incoming")
  public void consume(ProcessInstance processInstance) {
    LOG.info("Received process instance: " + processInstance);
    ConcurrentLinkedQueue<ProcessInstance> processInstances1 = processInstanceMap.computeIfAbsent(
        processInstance.getProcessInstanceKey(), k -> new ConcurrentLinkedQueue<>());
    processInstances1.add(processInstance);
  }

  @Incoming("process-definition-parsed-incoming")
  public void consume(ProcessDefinition processDefinition) {
    LOG.info("Received process definition: " + processDefinition);
    processDefinitionMap.put(ProcessDefinitionKey.of(processDefinition), processDefinition);
  }

  public BpmnTestEngine triggerNewProcessInstance(String elementId) {
    ProcessInstanceKey processInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
    Trigger trigger = new FlowElementTrigger(processInstanceKey, ProcessInstanceKey.NONE,
        activeProcessDefintion, new BaseElementId(elementId), BaseElementId.NONE, Variables.EMPTY);
    triggerEmitter.send(trigger);
    return this;
  }

  public ExternalTaskTrigger pollExternalTask(String elementId) {
    ConcurrentLinkedQueue<ExternalTaskTrigger> externalTaskTriggers = externalTaskTriggerMap.get(activeProcessInstanceKey);
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
    String xml = IOUtils.toString(BpmnTestEngine.class.getResourceAsStream(filename));
    Integer generation = GenerationExtractor.getGenerationFromString(filename).orElseThrow();
    Definitions definitions = BpmnParser.parse(xml, generation);
    xmlEmitter.send(KafkaRecord.of(filename, xml));

    activeProcessDefintion = Awaitility.await()
        .until(() -> {
          for (ProcessDefinitionKey key : processDefinitionMap.keySet()) {
            if (key.getProcessDefinitionId().equals(definitions.getProcessDefinitionId())
                && key.getGeneration().equals(generation)) {
              return processDefinitionMap.get(key);
            }
          }
          return null;
        }, Objects::nonNull);

    return this;
  }

  public BpmnTestEngine startProcessInstance(Variables variables) {
    ProcessDefinitionKey processDefinitionKey = ProcessDefinitionKey.of(activeProcessDefintion);
    BaseElementId startEventId = activeProcessDefintion.getDefinitions().getRootProcess().getFlowElements()
        .getStartEvents().get(0).getId();
    ProcessInstanceStartCommand startCommand = new ProcessInstanceStartCommand(
        processDefinitionKey, startEventId, variables);
    startCommandEmitter.send(KafkaRecord.of(processDefinitionKey, startCommand));

    activeProcessInstanceKey = Awaitility.await()
        .until(() -> {
          ConcurrentLinkedQueue<FlowElementTrigger> flowElementTriggers = definitionToInstancesMap.get(
              processDefinitionKey);
          if (flowElementTriggers == null || flowElementTriggers.isEmpty()) {
            return null;
          }
          FlowElementTrigger poll = flowElementTriggers.poll();
          if (poll != null && poll.getProcessDefinition() != null && ProcessDefinitionKey.of(poll.getProcessDefinition()).equals(processDefinitionKey)) {
            return poll.getProcessInstanceKey();
          }
          return null;
        }, Objects::nonNull);

    activeProcessInstance = Awaitility.await()
        .until(() -> {
          ConcurrentLinkedQueue<ProcessInstance> processInstances = processInstanceMap.get(
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
                .getId().equals(elementId));
    return this;
  }
  public BpmnTestEngine andRespondWithSuccess(Variables of) {
    triggerExternalTaskResponse(activeExternalTaskTrigger, new ExternalTaskResponseResult(true, ""), of);
    return this;
  }

  public BpmnTestEngine waitUntilCompleted() {

    activeProcessInstance = Awaitility.await()
        .until(() -> {
          ConcurrentLinkedQueue<ProcessInstance> processInstances = processInstanceMap.get(
              activeProcessInstanceKey);
          if (processInstances == null || processInstances.isEmpty()) {
            return null;
          }
          ProcessInstance poll = processInstances.poll();
          if (poll != null && poll.getProcessInstanceKey() != null && poll.getProcessInstanceKey()
              .equals(activeProcessInstanceKey) && poll.getProcessInstanceState().isFinished()) {
            return poll;
          }
          return null;
        }, Objects::nonNull);
    return this;
  }

  public ProcessInstanceAssert assertThatProcess() {
    return new ProcessInstanceAssert(activeProcessInstance);
  }

  public void clear() {
    processInstanceMap.clear();
    externalTaskTriggerMap.clear();
    processDefinitionMap.clear();
    definitionToInstancesMap.clear();
  }
}
