package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import static nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinitionState.ACTIVE;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import nl.qunit.bpmnmeister.engine.ProcessInstanceProcessor;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinitionService;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class ProcessIntanceService {
  private static final String QUERY_PROCESSINSTANCE = "processInstanceId = :pid";
  final ProcessInstanceProcessor processInstanceProcessor;

  final Emitter<ProcessInstanceTrigger> triggerEmitter;

  final ProcessDefinitionService processDefinitionService;
  final ProcessInstanceRepository processInstanceRepository;

  public ProcessIntanceService(
      ProcessInstanceProcessor processInstanceProcessor,
      @Channel("trigger-outgoing") Emitter<ProcessInstanceTrigger> triggerEmitter,
      ProcessDefinitionService processDefinitionService,
      ProcessInstanceRepository processInstanceRepository) {
    this.processInstanceProcessor = processInstanceProcessor;
    this.triggerEmitter = triggerEmitter;
    this.processDefinitionService = processDefinitionService;
    this.processInstanceRepository = processInstanceRepository;
  }

  public void startNewProcessInstance(
      String processDefinitionId, long version, String startElement) {
    Optional<Definitions> processDefinition =
        processDefinitionService.getProcessDefinition(processDefinitionId, version);
    processDefinition.ifPresent(
        definitions -> {
          if (definitions.getState() == ACTIVE) {
            startNewProcessInstance(definitions, startElement);
          }
        });
  }

  public void startNewProcessInstance(Definitions processDefinition, String startElement) {
    Log.infof(
        "Starting new process instance processdefinition %s, version %d, startelement %s",
        processDefinition.getProcessDefinitionId(), processDefinition.getVersion(), startElement);

    String startElementId;
    if (startElement == null) {
      startElementId =
          processDefinition.getStartEvents().stream().findFirst().orElseThrow().getId();
    } else {
      if (processDefinition.getFlowElement(startElement).isPresent()) {
        startElementId = startElement;
      } else {
        throw new NoSuchElementException(
            "Startevent "
                + startElement
                + " not found in process timeCycle "
                + processDefinition.getProcessDefinitionId());
      }
    }

    ProcessInstance processInstance =
        ProcessInstance.builder()
            .processInstanceId(UUID.randomUUID())
            .processDefinitionId(processDefinition.getProcessDefinitionId())
            .version(processDefinition.getVersion())
            .elementStates(new HashMap<>())
            .build();
    processInstanceRepository.persist(processInstance);

    triggerProcess(
        new ProcessInstanceTrigger(
            processInstance.getProcessInstanceId(),
            processDefinition.getProcessDefinitionId(),
            processDefinition.getVersion(),
            startElementId,
            null),
        processDefinition,
        processInstance);
  }

  public void consumeTrigger(ProcessInstanceTrigger trigger) {
    if (trigger.processInstanceId() == null) {
      startNewProcessInstance(
          trigger.processDefinitionId(), trigger.version(), trigger.elementId());
    } else {
      triggerExistingProcessInstance(trigger);
    }
  }

  private void triggerExistingProcessInstance(ProcessInstanceTrigger trigger) {
    Parameters queryparameters = Parameters.with("pid", trigger.processInstanceId());
    Optional<ProcessInstance> opi =
        processInstanceRepository
            .find(QUERY_PROCESSINSTANCE, queryparameters)
            .firstResultOptional();
    if (opi.isPresent()) {
      ProcessInstance pi = opi.get();
      Optional<Definitions> pd =
          processDefinitionService.getProcessDefinition(
              pi.getProcessDefinitionId(), pi.getVersion());
      if (pd.isPresent()) {
        triggerProcess(trigger, pd.get(), pi);
      } else {
        Log.errorf(
            "Process definition %s version %d not found",
            pi.getProcessDefinitionId(), pi.getVersion());
      }
    } else {
      Log.errorf("Process instance %s not found", trigger.processInstanceId());
    }
  }

  private void triggerProcess(ProcessInstanceTrigger trigger, Definitions pd, ProcessInstance pi) {
    Set<ProcessInstanceTrigger> newTriggers = processInstanceProcessor.trigger(pd, pi, trigger);

    processInstanceRepository.persistOrUpdate(pi);

    newTriggers.forEach(triggerEmitter::send);
  }
}
