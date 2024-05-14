package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Gateway;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.GatewayState;

public abstract class GatewayProcessor<G extends Gateway, S extends GatewayState>
    extends StateProcessor<G, S> {

  @Override
  protected TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      G element,
      S oldState,
      Variables variables) {
    if (oldState.getState() == ActivityStateEnum.READY
        || oldState.getState() == ActivityStateEnum.ACTIVE) {
      return triggerDecision(trigger, processInstance, definition, element, oldState, variables);
    }
    return new TriggerResult(
        oldState,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }

  protected abstract TriggerResult triggerDecision(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      G element,
      S oldState,
      Variables variables);
}
