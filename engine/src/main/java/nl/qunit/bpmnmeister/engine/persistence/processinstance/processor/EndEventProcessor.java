package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.EndEvent;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.EndEventState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;

@ApplicationScoped
public class EndEventProcessor extends StateProcessor<EndEvent, EndEventState> {

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      Definitions processDefinition,
      EndEvent element,
      EndEventState oldState) {
    return new TriggerResult(EndEventState.builder().state(StateEnum.FINISHED).build(), Set.of());
  }

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      Definitions processDefinition,
      EndEvent element,
      EndEventState oldState) {
    return new TriggerResult(EndEventState.builder().state(StateEnum.FINISHED).build(), Set.of());
  }

  @Override
  public EndEventState initialState() {
    return EndEventState.builder().state(StateEnum.INIT).build();
  }
}
