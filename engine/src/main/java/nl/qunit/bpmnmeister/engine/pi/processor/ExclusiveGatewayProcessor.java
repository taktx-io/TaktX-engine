package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.ExclusiveGateway;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.ExclusiveGatewayState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ExclusiveGatewayProcessor
    extends GatewayProcessor<ExclusiveGateway, ExclusiveGatewayState> {
  private static final Logger LOG = Logger.getLogger(ExclusiveGatewayProcessor.class);

  @Override
  protected TriggerResult triggerDecision(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ExclusiveGateway element,
      ExclusiveGatewayState oldState) {
    return TriggerResult.builder()
        .newElementState(
            new ExclusiveGatewayState(StateEnum.FINISHED, oldState.getElementInstanceId()))
        .newActiveFlows(element.getOutgoing())
        .build();
  }

  @Override
  public ExclusiveGatewayState initialState() {
    return new ExclusiveGatewayState(StateEnum.INIT, UUID.randomUUID());
  }

  @Override
  public ExclusiveGatewayState terminate(ExclusiveGatewayState oldState) {
    return new ExclusiveGatewayState(StateEnum.TERMINATED, oldState.getElementInstanceId());
  }
}
