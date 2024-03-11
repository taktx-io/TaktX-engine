package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.Gateway;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;
import nl.qunit.bpmnmeister.pi.state.GatewayState;

public abstract class GatewayProcessor<G extends Gateway, S extends GatewayState>
    extends StateProcessor<G, S> {

  @Override
  public TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      BaseElement element,
      BpmnElementState oldState,
      Map<String, JsonNode> variables) {
    return triggerDecision(trigger, processInstance, (G) element, (S) oldState);
  }

  protected abstract TriggerResult triggerDecision(
      ProcessInstanceTrigger trigger, ProcessInstance processInstance, G element, S oldState);
}
