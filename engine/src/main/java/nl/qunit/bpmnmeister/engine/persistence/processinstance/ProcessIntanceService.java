package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import nl.qunit.bpmnmeister.engine.ProcessInstanceProcessor;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinitionService;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class ProcessIntanceService {
  private static final String QUERY_PROCESSINSTANCE = "processInstanceId = :pid";
  final ProcessInstanceProcessor processInstanceProcessor;

  final Emitter<Trigger> triggerEmitter;

  final ProcessDefinitionService processDefinitionService;
  final ProcessInstanceRepository processInstanceRepository;

  public ProcessIntanceService(
      ProcessInstanceProcessor processInstanceProcessor,
      @Channel("trigger-outgoing") Emitter<Trigger> triggerEmitter,
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
    if (processDefinition.isPresent()) {
      startNewProcessInstance(processDefinition.get(), startElement);
    }
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
        new Trigger(
            processInstance.getProcessInstanceId(),
            processDefinition.getProcessDefinitionId(),
            processDefinition.getVersion(),
            startElementId,
            null,
            null),
        processDefinition,
        processInstance);
  }

  public void consumeTrigger(Trigger trigger) {
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

  private void triggerProcess(Trigger trigger, Definitions pd, ProcessInstance pi) {
    Set<Trigger> newTriggers = processInstanceProcessor.trigger(pd, pi, trigger);

    processInstanceRepository.persistOrUpdate(pi);

    newTriggers.forEach(triggerEmitter::send);
  }
}
