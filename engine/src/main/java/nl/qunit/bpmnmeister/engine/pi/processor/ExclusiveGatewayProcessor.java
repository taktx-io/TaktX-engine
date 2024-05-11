package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;

@ApplicationScoped
public class ExclusiveGatewayProcessor
    extends GatewayProcessor<ExclusiveGateway, ExclusiveGatewayState> {
  @Override
  protected TriggerResult triggerDecision(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ExclusiveGateway element,
      ExclusiveGatewayState oldState) {
    return new TriggerResult(
        new ExclusiveGatewayState(
            UUID.randomUUID(), oldState.getPassedCnt() + 1, ActivityStateEnum.ACTIVE),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Variables.EMPTY);
  }
}
