package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Event;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.EventState;
import org.jboss.logging.Logger;

public abstract class EventProcessor<E extends Event, S extends EventState>
    extends StateProcessor<E, S> {
  private static final Logger LOG = Logger.getLogger(EventProcessor.class);

  @Override
  public TriggerResult dotrigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Variables variables) {
    if (trigger.isTerminate()) {
      return new TriggerResult(
          terminate((S) oldState), Set.of(), Set.of(), Set.of(), Variables.EMPTY);
    }

    return triggerEvent(trigger, processInstance, (E) element, (S) oldState);
  }

  protected abstract TriggerResult triggerEvent(
      ProcessInstanceTrigger trigger, ProcessInstance processInstance, E element, S oldState);
}
