package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

public abstract class StateProcessor<E extends BaseElement, S extends BpmnElementState> {

  public final TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      BaseElement element,
      BpmnElementState oldState,
      Variables variables) {
    return dotrigger(trigger, processInstance, (E) element, (S) oldState, variables);
  }

  protected abstract TriggerResult dotrigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      E element,
      S oldState,
      Variables variables);

  public abstract S initialState();

  public abstract S terminate(S oldState);
}
