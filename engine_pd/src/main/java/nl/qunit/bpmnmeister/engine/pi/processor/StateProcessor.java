package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

public abstract class StateProcessor<E extends BaseElement, S extends BpmnElementState> {
  public TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessDefinition processDefinition,
      BaseElement element,
      BpmnElementState oldState) {
    return switch (oldState.getState()) {
      case INIT -> triggerWhenInit(trigger, processDefinition, (E) element, (S) oldState);
      case ACTIVE -> triggerWhenActive(trigger, processDefinition, (E) element, (S) oldState);
      case WAITING -> triggerWhenWaiting(trigger, processDefinition, (E) element, (S) oldState);
      case FINISHED -> triggerWhenFinished(trigger, processDefinition, (E) element, (S) oldState);
      default -> throw new IllegalStateException("Unknown state: " + oldState.getState());
    };
  }

  protected abstract TriggerResult triggerWhenFinished(
      ProcessInstanceTrigger trigger, ProcessDefinition processDefinition, E element, S oldState);

  protected abstract TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger, ProcessDefinition processDefinition, E element, S oldState);

  protected abstract TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger, ProcessDefinition processDefinition, E element, S oldState);

  protected abstract TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger, ProcessDefinition processDefinition, E element, S oldState);

  public abstract S initialState();
}
