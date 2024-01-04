package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.ProcessInstanceProcessor;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinitionService;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ProcessIntanceService {
  private static final String QUERY_PROCESSINSTANCE = "processInstanceId = :pid";
  final ProcessInstanceProcessor processInstanceProcessor;

  final Emitter<Trigger> triggerEmitter;

  final ProcessDefinitionService processDefinitionService;
  final ProcessInstanceRepository processInstanceRepository;

  public ProcessIntanceService(ProcessInstanceProcessor processInstanceProcessor,
                               @Channel("trigger-outgoing")
  Emitter<Trigger> triggerEmitter, ProcessDefinitionService processDefinitionService, ProcessInstanceRepository processInstanceRepository) {
    this.processInstanceProcessor = processInstanceProcessor;
    this.triggerEmitter = triggerEmitter;
    this.processDefinitionService = processDefinitionService;
    this.processInstanceRepository = processInstanceRepository;
  }

  public void startNewProcessInstance(
          String processDefinitionId, long version, String startElement) {
    Definitions processDefinition =
            processDefinitionService.getProcessDefinition(processDefinitionId, version);
    startNewProcessInstance(processDefinition, startElement);
  }

  public void startNewProcessInstance(Definitions processDefinition, String startElement) {
    ProcessInstance processInstance =
            ProcessInstance.builder()
                    .processInstanceId(UUID.randomUUID())
                    .processDefinitionId(processDefinition.getProcessDefinitionId())
                    .version(processDefinition.getVersion())
                    .elementStates(new HashMap<>())
                    .build();
    processInstanceRepository.persist(processInstance);

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
    triggerProcess(
            new Trigger(processInstance.getProcessInstanceId(), startElementId, null, null),
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
    Definitions pd =
            processDefinitionService.getProcessDefinition(pi.getProcessDefinitionId(), pi.getVersion());

    triggerProcess(trigger, pd, pi);
  }

  private void triggerProcess(Trigger trigger, Definitions pd, ProcessInstance pi) {
    Set<Trigger> newTriggers = processInstanceProcessor.trigger(pd, pi, trigger);

    processInstanceRepository.persistOrUpdate(pi);

    newTriggers.forEach(triggerEmitter::send);
  }
}
