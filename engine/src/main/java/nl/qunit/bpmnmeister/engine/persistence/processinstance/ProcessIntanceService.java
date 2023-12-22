package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import nl.qunit.bpmnmeister.engine.ProcessInstanceProcessor;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class ProcessIntanceService {
  private static final String QUERY_PROCESSINSTANCE = "processInstanceId = :pid";
  @Inject ProcessInstanceProcessor processInstanceProcessor;

  @Inject
  @Channel("trigger-outgoing")
  Emitter<Trigger> triggerEmitter;

  @Inject ProcessDefinitionService processDefinitionService;
  @Inject ProcessInstanceRepository processInstanceRepository;

  public void startNewProcessInstance(String processDefinitionId, long version, String startevent) {
    ProcessDefinition processDefinition =
        processDefinitionService.getProcessDefinition(processDefinitionId, version);
    ProcessInstance processInstance =
        new ProcessInstance(
            null,
            UUID.randomUUID(),
            processDefinitionId,
            processDefinition.version,
            new HashMap<>());
    processInstanceRepository.persist(processInstance);

    String startElementId;
    if (startevent == null) {
      startElementId =
          processDefinition.bpmnElements.values().stream()
              .filter(StartEvent.class::isInstance)
              .findFirst()
              .orElseThrow()
              .getId();
    } else {
      if (processDefinition.bpmnElements.containsKey(startevent)) {
        startElementId = startevent;
      } else {
        throw new NoSuchElementException(
            "Startevent " + startevent + " not found in process definition " + processDefinitionId);
      }
    }
    triggerProcess(
        new Trigger(processInstance.processInstanceId, startElementId, null),
        processDefinition,
        processInstance);
  }

  public void consumeTrigger(Trigger trigger) {
    Parameters queryparameters = Parameters.with("pid", trigger.processInstanceId());

    ProcessInstance pi =
        processInstanceRepository
            .find(QUERY_PROCESSINSTANCE, queryparameters)
            .firstResultOptional()
            .orElseThrow();
    ProcessDefinition pd =
        processDefinitionService.getProcessDefinition(pi.processDefinitionId, pi.version);

    triggerProcess(trigger, pd, pi);
  }

  private void triggerProcess(Trigger trigger, ProcessDefinition pd, ProcessInstance pi) {
    Set<Trigger> newTriggers = processInstanceProcessor.trigger(pd, pi, trigger);

    processInstanceRepository.persistOrUpdate(pi);

    newTriggers.forEach(triggerEmitter::send);
  }
}
