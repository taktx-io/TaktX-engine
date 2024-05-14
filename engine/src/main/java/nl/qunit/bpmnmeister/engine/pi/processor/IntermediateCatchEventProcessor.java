package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pd.MessageSchedulerFactory;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventStateEnum;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@ApplicationScoped
public class IntermediateCatchEventProcessor
    extends EventProcessor<IntermediateCatchEvent, IntermediateCatchEventState> {
  @Inject MessageSchedulerFactory messageSchedulerFactory;

  @Override
  protected TriggerResult triggerEvent(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      IntermediateCatchEvent element,
      IntermediateCatchEventState oldState) {
    if (oldState.getState() == IntermediateCatchEventStateEnum.READY) {
      return scheduleEvents(processInstance, element, oldState);
    } else if (oldState.getState() == IntermediateCatchEventStateEnum.WAITING) {
      if (trigger.getInputFlowId().equals(Constants.NONE)) {
        return timerTriggered(oldState, element);
      } else {
        return scheduleEvents(processInstance, element, oldState);
      }
    } else {
      return new TriggerResult(
          new IntermediateCatchEventState(
              oldState.getElementInstanceId(),
              oldState.getPassedCnt(),
              oldState.getState(),
              oldState.getScheduledKeys()),
          Set.of(),
          Set.of(),
          Set.of(),
          Set.of(),
          ThrowingEvent.NOOP,
          Set.of(),
          Set.of(),
          Variables.EMPTY);
    }
  }

  private static TriggerResult timerTriggered(
      IntermediateCatchEventState oldState, IntermediateCatchEvent element) {
    return new TriggerResult(
        new IntermediateCatchEventState(
            oldState.getElementInstanceId(),
            oldState.getPassedCnt() + 1,
            IntermediateCatchEventStateEnum.WAITING,
            oldState.getScheduledKeys()),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }

  private TriggerResult scheduleEvents(
      ProcessInstance processInstance,
      IntermediateCatchEvent element,
      IntermediateCatchEventState oldState) {
    List<SchedulableMessage<?>> messages =
        List.of(
            new FlowElementTrigger(
                processInstance.getProcessInstanceKey(),
                element.getId(),
                Constants.NONE,
                Variables.EMPTY));
    Set<MessageScheduler> messageSchedulers =
        element.getTimerEventDefinitions().stream()
            .map(
                timerEventDefinition ->
                    messageSchedulerFactory.schedule(
                        processInstance.getProcessDefinitionKey(),
                        processInstance.getProcessInstanceKey(),
                        element.getId(),
                        timerEventDefinition,
                        messages,
                        processInstance.getVariables()))
            .collect(Collectors.toSet());

    return new TriggerResult(
        new IntermediateCatchEventState(
            oldState.getElementInstanceId(),
            oldState.getPassedCnt(),
            IntermediateCatchEventStateEnum.WAITING,
            messageSchedulers.stream()
                .map(MessageScheduler::getScheduleKey)
                .collect(Collectors.toSet())),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        ThrowingEvent.NOOP,
        messageSchedulers,
        oldState.getScheduledKeys(), // Cancel any old schedules
        Variables.EMPTY);
  }
}
