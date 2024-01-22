package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BaseElement;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.BpmnElementState;

public abstract class StateProcessor<E extends BaseElement, S extends BpmnElementState> {
  public TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      Definitions processDefinition,
      BaseElement element,
      BpmnElementState oldState) {
    return switch (oldState.getState()) {
      case INIT -> triggerWhenInit(trigger, processDefinition, (E) element, (S) oldState);
      case ACTIVE -> triggerWhenActive(trigger, processDefinition, (E) element, (S) oldState);
      default -> throw new IllegalStateException("Unknown state: " + oldState.getState());
    };
  }

  protected abstract TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger, Definitions processDefinition, E element, S oldState);

  protected abstract TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger, Definitions processDefinition, E element, S oldState);

  public abstract S initialState();
}
