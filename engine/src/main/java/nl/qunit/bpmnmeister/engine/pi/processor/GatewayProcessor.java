package nl.qunit.bpmnmeister.engine.pi.processor;

import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Gateway;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.GatewayState;

public abstract class GatewayProcessor<G extends Gateway, S extends GatewayState>
    extends StateProcessor<G, S> {

  @Override
  public TriggerResult dotrigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      G element,
      S oldState,
      Variables variables) {
    return triggerDecision(trigger, processInstance, (G) element, (S) oldState);
  }

  protected abstract TriggerResult triggerDecision(
      ProcessInstanceTrigger trigger, ProcessInstance processInstance, G element, S oldState);
}
