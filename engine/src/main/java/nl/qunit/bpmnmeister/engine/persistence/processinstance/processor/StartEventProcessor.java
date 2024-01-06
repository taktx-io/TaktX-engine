package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.StartEvent;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StartEventState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;

@ApplicationScoped
public class StartEventProcessor extends StateProcessor<StartEvent, StartEventState> {

  @Override
  protected TriggerResult triggerWhenInit(
      Trigger trigger,
      Definitions processDefinition,
      StartEvent element,
      StartEventState oldState) {
    return new TriggerResult(
        StartEventState.builder()
            .state(StateEnum.ACTIVE)
            .activeTimerIds(Set.of(trigger.timerId()))
            .build(),
        element.getOutgoing());
  }

  @Override
  protected TriggerResult triggerWhenActive(
      Trigger trigger,
      Definitions processDefinition,
      StartEvent element,
      StartEventState oldState) {
    return new TriggerResult(
        StartEventState.builder()
            .state(StateEnum.ACTIVE)
            .activeTimerIds(Set.of(trigger.timerId()))
            .build(),
        element.getOutgoing());
  }

  @Override
  public StartEventState initialState() {
    return StartEventState.builder().state(StateEnum.INIT).activeTimerIds(new HashSet<>()).build();
  }
}
