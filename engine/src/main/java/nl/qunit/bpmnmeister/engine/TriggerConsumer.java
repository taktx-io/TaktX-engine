package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessIntanceService;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.Trigger;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TriggerConsumer {
  private static final Logger LOG = Logger.getLogger(TriggerConsumer.class);

  @Inject ProcessIntanceService processInstanceService;

  @Incoming("trigger-incoming")
  public void consume(Trigger trigger) {
    processInstanceService.consumeTrigger(trigger);
  }
}
