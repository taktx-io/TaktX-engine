package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.CatchEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.state.CatchEventState;

public abstract class CatchEventProcessor<E extends CatchEvent<?>, S extends CatchEventState>
    extends EventProcessor<E, S> {

  @Override
  protected void triggerEventStart(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables) {

    triggerCatchEventStart(
        triggerResultBuilder,
        trigger,
        processInstance,
        processDefinition,
        element,
        oldState,
        variables);
  }

  @Override
  protected void triggerEventContinue(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      TriggerResultBuilder triggerResultBuilder,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected abstract void triggerCatchEventStart(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables);
}
