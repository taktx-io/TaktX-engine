package nl.qunit.bpmnmeister;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBException;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.ProcessInstanceEntity;
import nl.qunit.bpmnmeister.engine.ProcessInstanceProcessor;
import nl.qunit.bpmnmeister.engine.ProcessInstanceRepository;
import nl.qunit.bpmnmeister.engine.xml.BpmnParser;
import nl.qunit.bpmnmeister.model.processdefinition.ProcessDefinition;
import nl.qunit.bpmnmeister.model.processinstance.ProcessInstance;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;
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

  @Inject BpmnParser bpmnParser;
  @Inject ProcessInstanceRepository processInstanceRepository;

  private final ProcessInstance pi = new ProcessInstance(UUID.randomUUID(), new HashMap<>());
  private ProcessDefinition pd;

  @Incoming("trigger-incoming")
  public void consume(Trigger trigger) {

    LOG.info("Received trigger: " + trigger);
    try {
      if (pd == null) {
        pd = bpmnParser.parse();
      }
    } catch (JAXBException e) {
      throw new RuntimeException(e);
    }
    Set<Trigger> newTriggers = processInstanceProcessor.trigger(pd, pi, trigger);
    ProcessInstanceEntity pie =
        ProcessInstanceEntity.builder()
            .processInstanceId(pi.processInstanceId().toString())
            .build();
    processInstanceRepository.persist(pie);
    newTriggers.forEach(triggerEmitter::send);
  }
}
