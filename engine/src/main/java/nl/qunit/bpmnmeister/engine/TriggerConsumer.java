package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessIntanceService;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class TriggerConsumer {
  @Inject ProcessIntanceService processInstanceService;

  @Incoming("trigger-incoming")
  public void consume(ProcessInstanceTrigger trigger) {
    processInstanceService.consumeTrigger(trigger);
  }
}
