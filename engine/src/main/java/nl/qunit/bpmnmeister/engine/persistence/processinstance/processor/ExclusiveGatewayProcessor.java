package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ExclusiveGateway;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.ExclusiveGatewayState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;

@ApplicationScoped
public class ExclusiveGatewayProcessor
    extends StateProcessor<ExclusiveGateway, ExclusiveGatewayState> {

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      Definitions processDefinition,
      ExclusiveGateway element,
      ExclusiveGatewayState oldState) {
    return new TriggerResult(ExclusiveGatewayState.builder().build(), element.getOutgoing());
  }

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      Definitions processDefinition,
      ExclusiveGateway element,
      ExclusiveGatewayState oldState) {
    return new TriggerResult(ExclusiveGatewayState.builder().build(), element.getOutgoing());
  }

  @Override
  public ExclusiveGatewayState initialState() {
    return ExclusiveGatewayState.builder().state(StateEnum.INIT).build();
  }
}
