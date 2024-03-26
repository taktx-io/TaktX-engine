package nl.qunit.bpmnmeister.engine.pi;


import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import nl.qunit.bpmnmeister.Topics;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.Trigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityState;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@ApplicationScoped
public class BpmnTestEngine {

  private static final Logger LOG = Logger.getLogger(BpmnTestEngine.class);
  private static final Duration DEFAULT_DURATION = Duration.ofSeconds(10);
  private static final long DEFAULT_INTERVAL_MILLIS = 1000;

  @Inject
  AdminClient adminClient;

  @Inject
  @Channel("process-instance-trigger-outgoing")
  Emitter<Trigger> triggerEmitter;

  private Map<ProcessInstanceKey, ConcurrentLinkedQueue<ProcessInstance>> processInstanceMap = new HashMap<>();
  private Map<ProcessInstanceKey, ConcurrentLinkedQueue<ExternalTaskTrigger>> externalTaskTriggerMap = new HashMap<>();


  @PostConstruct
  void init() {
    adminClient.createTopics(
        Arrays.stream(Topics.values())
            .map(topic -> new NewTopic(topic.getTopicName(), 1, (short) 1))
            .toList());
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

  public ProcessInstanceKey triggerNewProcessInstance(ProcessDefinition processDefinition,
      BaseElementId elementId) {
    ProcessInstanceKey processInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
    Trigger trigger = new FlowElementTrigger(processInstanceKey, ProcessInstanceKey.NONE,
        processDefinition, elementId, BaseElementId.NONE, Variables.EMPTY);
    triggerEmitter.send(trigger);
    return processInstanceKey;
  }

  public ProcessInstance waitUntilCompleted(ProcessInstanceKey processInstanceKey) {
    return waitUntilCompleted(processInstanceKey, DEFAULT_DURATION);
  }

  public ProcessInstance waitUntilCompleted(ProcessInstanceKey processInstanceKey, Duration duration) {
    return Awaitility.await().atMost(duration).until(() -> pollProcessInstance(processInstanceKey),
        BpmnTestEngine::isCompleted);
  }

  private static boolean isCompleted(@Nullable ProcessInstance processInstance) {
    return processInstance != null
           && processInstance.getProcessInstanceState() == ProcessInstanceState.COMPLETED;
  }

  @Nullable
  private ProcessInstance pollProcessInstance(ProcessInstanceKey processInstanceKey) {
    ConcurrentLinkedQueue<ProcessInstance> processInstances = processInstanceMap.get(
        processInstanceKey);
    if (processInstances == null) {
      return null;
    }
    return processInstances.poll();
  }

  public ExternalTaskTrigger waitUntilServiceTaskIsWaitingForResponse(ProcessInstanceKey processInstanceKey,
      String elementId) {
    return waitUntilServiceTaskIsWaitingForResponse(processInstanceKey, elementId, DEFAULT_DURATION);
  }

  public ExternalTaskTrigger waitUntilServiceTaskIsWaitingForResponse(ProcessInstanceKey processInstanceKey,
      String elementId, Duration duration) {
    return Awaitility.await().atMost(duration).until(() -> pollExternalTask(processInstanceKey, elementId),
        externalTaskTrigger -> externalTaskTrigger != null && externalTaskTrigger.getElementId().getId().equals(elementId));
  }

  private ExternalTaskTrigger pollExternalTask(ProcessInstanceKey processInstanceKey, String elementId) {
    ConcurrentLinkedQueue<ExternalTaskTrigger> externalTaskTriggers = externalTaskTriggerMap.get(
        processInstanceKey);
    if (externalTaskTriggers == null) {
      return null;
    }
    return externalTaskTriggers.poll();
  }

}
