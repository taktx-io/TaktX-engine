package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Event;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.EventState;

public abstract class EventProcessor<E extends Event<?>, S extends EventState>
    extends StateProcessor<E, S> {

  @Override
  public TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      Variables variables) {
    return triggerEvent(trigger, processInstance, element, oldState);
  }

  protected abstract TriggerResult triggerEvent(
      ProcessInstanceTrigger trigger, ProcessInstance processInstance, E element, S oldState);
}
