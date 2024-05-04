package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ExternalTaskResponseTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

public abstract class StateProcessor<E extends BaseElement, S extends BpmnElementState> {

  public final TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      BaseElement element,
      BpmnElementState oldState,
      Variables variables) {
    if (trigger instanceof FlowElementTrigger flowElementTrigger) {
      return triggerFlowElement(
          flowElementTrigger, processInstance, definition, (E) element, (S) oldState, variables);
    } else if (trigger instanceof ExternalTaskResponseTrigger externalTaskResponse) {
      return triggerExternalTaskResponse(
          externalTaskResponse, processInstance, definition, (E) element, (S) oldState, variables);
    }
    throw new IllegalStateException("Unknown trigger type: " + trigger);
  }

  protected abstract TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      Variables variables);

  protected TriggerResult triggerExternalTaskResponse(
      ExternalTaskResponseTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      Variables variables) {
    return new TriggerResult(
        oldState,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Variables.EMPTY);
  }

  public abstract S initialState();
}
