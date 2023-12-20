package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinition;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinitionRepository;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstance;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstanceRepository;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.Trigger;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TriggerConsumer {
  private static final Logger LOG = Logger.getLogger(TriggerConsumer.class);

  @Inject ProcessInstanceProcessor processInstanceProcessor;

  @Inject
  @Channel("trigger-outgoing")
  Emitter<Trigger> triggerEmitter;

  @Inject ProcessDefinitionRepository processDefinitionRepository;
  @Inject ProcessInstanceRepository processInstanceRepository;

  @Incoming("trigger-incoming")
  public void consume(Trigger trigger) {

    LOG.info("Received trigger: " + trigger);
    ProcessInstance pi =
        processInstanceRepository
            .find("processInstanceId", trigger.processInstanceId())
            .firstResult();
    ProcessDefinition pd =
        processDefinitionRepository
            .find("processDefinitionId", pi.processDefinitionId())
            .firstResult();
    Set<Trigger> newTriggers = processInstanceProcessor.trigger(pd, pi, trigger);
    processInstanceRepository.persist(pi);
    newTriggers.forEach(triggerEmitter::send);
  }
}
