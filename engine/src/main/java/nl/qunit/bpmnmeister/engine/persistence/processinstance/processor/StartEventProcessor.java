package nl.qunit.bpmnmeister.engine.persistence.processinstance.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StartEventState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.StateEnum;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;
import nl.qunit.bpmnmeister.model.scheduler.RecurringCommand;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
@RequiredArgsConstructor
public class StartEventProcessor extends StateProcessor<StartEvent, StartEventState> {
  @Inject
  @Channel("recurring-outgoing")
  Emitter<RecurringCommand> recurringCommandEmitter;

  @Override
  public TriggerResult doTrigger(
      Trigger trigger,
      Definitions processDefinition,
      StartEvent element,
      StartEventState oldState) {
    if (element.getTimerEventDefinitions().isEmpty()) {
      // No timerevents, just continue
      return new TriggerResult(StartEventState.builder().build(), element.getOutgoing());
    } else {
      // Schedule the timerevents
      Set<String> activeTimerIds = oldState.getActiveTimerIds();
      element
          .getTimerEventDefinitions()
          .forEach(
              timerEventDefinition -> {
                String timerId = timerEventDefinition.getId();
                List<Trigger> scheduledTriggers =
                    element.getOutgoing().stream()
                        .map(
                            flowId -> {
                              SequenceFlow flow =
                                  (SequenceFlow)
                                      processDefinition.getFlowElement(flowId).orElseThrow();
                              String targetId = flow.getTarget();
                              return new Trigger(trigger.processInstanceId(), targetId, flowId);
                            })
                        .toList();
                String cron = timerEventDefinition.getTimeCycle();
                RecurringCommand msg = new RecurringCommand(timerId, scheduledTriggers, cron);
                recurringCommandEmitter.send(msg);
                activeTimerIds.add(timerId);
              });
      return new TriggerResult(
          StartEventState.builder().state(StateEnum.ACTIVE).activeTimerIds(activeTimerIds).build(),
          Set.of());
    }
  }

  @Override
  public StartEventState initialState() {
    return StartEventState.builder().state(StateEnum.INIT).activeTimerIds(new HashSet<>()).build();
  }
}
