package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BaseElement;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.BpmnElementState;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;

public abstract class StateProcessor<E extends BaseElement, S extends BpmnElementState> {
  public TriggerResult trigger(
      Trigger trigger,
      Definitions processDefinition,
      BaseElement element,
      BpmnElementState oldState) {
    return doTrigger(trigger, processDefinition, (E) element, (S) oldState);
  }

  protected abstract TriggerResult doTrigger(
      Trigger trigger, Definitions processDefinition, E element, S oldState);

  public abstract S initialState();
}
