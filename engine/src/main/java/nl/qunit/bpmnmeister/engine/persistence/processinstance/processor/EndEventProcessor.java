package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.EndEvent;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.EndEventState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;

@ApplicationScoped
public class EndEventProcessor extends StateProcessor<EndEvent, EndEventState> {
  @Override
  public TriggerResult doTrigger(
      Trigger trigger, Definitions processDefinition, EndEvent element, EndEventState oldState) {
    return new TriggerResult(EndEventState.builder().build(), element.getOutgoing());
  }

  @Override
  public EndEventState initialState() {
    return EndEventState.builder().state(StateEnum.INIT).build();
  }
}
