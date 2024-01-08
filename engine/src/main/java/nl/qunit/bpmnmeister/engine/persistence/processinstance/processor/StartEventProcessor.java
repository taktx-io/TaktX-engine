package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.StartEvent;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StartEventState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;

@ApplicationScoped
public class StartEventProcessor extends StateProcessor<StartEvent, StartEventState> {

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      Definitions processDefinition,
      StartEvent element,
      StartEventState oldState) {
    return trigger(trigger, element);
  }

  @Override
  protected TriggerResult triggerWhenActive(
      ProcessInstanceTrigger trigger,
      Definitions processDefinition,
      StartEvent element,
      StartEventState oldState) {

    return trigger(trigger, element);
  }

  private static TriggerResult trigger(ProcessInstanceTrigger trigger, StartEvent element) {
    return new TriggerResult(
        StartEventState.builder().state(StateEnum.ACTIVE).build(), element.getOutgoing());
  }

  @Override
  public StartEventState initialState() {
    return StartEventState.builder().state(StateEnum.INIT).build();
  }
}
