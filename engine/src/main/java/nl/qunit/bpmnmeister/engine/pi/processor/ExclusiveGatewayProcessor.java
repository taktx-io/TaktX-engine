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
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ExclusiveGatewayProcessor
    extends GatewayProcessor<ExclusiveGateway, ExclusiveGatewayState> {
  private static final Logger LOG = Logger.getLogger(ExclusiveGatewayProcessor.class);

  @Override
  protected TriggerResult triggerDecision(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ExclusiveGateway element,
      ExclusiveGatewayState oldState) {
    return new TriggerResult(
        new ExclusiveGatewayState(UUID.randomUUID(), oldState.getPassedCnt() + 1),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Variables.EMPTY);
  }
}
