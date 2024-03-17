package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Event;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.EventState;
import org.jboss.logging.Logger;

public abstract class EventProcessor<E extends Event, S extends EventState>
    extends StateProcessor<E, S> {
  private static final Logger LOG = Logger.getLogger(EventProcessor.class);

  @Override
  public TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Variables variables) {
    return triggerEvent(trigger, processInstance, element, oldState);
  }

  protected abstract TriggerResult triggerEvent(
      FlowElementTrigger trigger, ProcessInstance processInstance, E element, S oldState);
}
