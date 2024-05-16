package nl.qunit.bpmnmeister.engine.pi.processor.flowelement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pd.MessageSchedulerFactory;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.processor.StateProcessor;
import nl.qunit.bpmnmeister.pd.model.BoundaryEvent;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.BoundaryEventState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@ApplicationScoped
public class BoundaryEventProcessor extends StateProcessor<BoundaryEvent, BoundaryEventState> {
  @Inject MessageSchedulerFactory messageSchedulerFactory;

  @Override
  public TriggerResult terminate(
      ProcessInstance processInstance,
      TerminateTrigger terminateTrigger,
      BoundaryEvent boundaryEvent,
      BoundaryEventState boundaryEventState) {

    // Cancel any schedules
    Set<ScheduleKey> cancelSchedules = new HashSet<>(boundaryEventState.getScheduleKeys());

    return TriggerResult.builder()
        .newFlowNodeState(
            new BoundaryEventState(
                boundaryEventState.getElementInstanceId(),
                boundaryEventState.getPassedCnt(),
                FlowNodeStateEnum.TERMINATED,
                boundaryEventState.getScheduleKeys(),
                boundaryEventState.getInputFlowId()))
        .cancelSchedules(cancelSchedules)
        .build();
  }

  @Override
  protected TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      BoundaryEvent element,
      BoundaryEventState oldState,
      Variables variables) {

    if (oldState.getState() == FlowNodeStateEnum.READY) {
      return startScheduler(processInstance, element, oldState, variables);
    } else if (oldState.getState() == FlowNodeStateEnum.ACTIVE) {
      return timeout(processInstance, element, oldState, variables);
    } else {
      // Any scheduled triggers will be ignored here
      return TriggerResult.builder().newFlowNodeState(oldState).build();
    }
  }

  private TriggerResult timeout(
      ProcessInstance processInstance,
      BoundaryEvent element,
      BoundaryEventState oldState,
      Variables variables) {

    Set<ProcessInstanceTrigger> cancelElementTriggers = new HashSet<>();
    if (element.isCancelActivity()) {
      ProcessInstanceTrigger cancelElementTrigger =
          new TerminateTrigger(processInstance.getProcessInstanceKey(), element.getAttachedToRef());
      cancelElementTriggers.add(cancelElementTrigger);
    }

    return TriggerResult.builder()
        .newFlowNodeState(
            new BoundaryEventState(
                oldState.getElementInstanceId(),
                oldState.getPassedCnt() + 1,
                oldState.getState(),
                oldState.getScheduleKeys(),
                oldState.getInputFlowId()))
        .newActiveFlows(element.getOutgoing())
        .newProcessInstanceTriggers(cancelElementTriggers)
        .variables(variables)
        .build();
  }

  private TriggerResult startScheduler(
      ProcessInstance processInstance,
      BoundaryEvent element,
      BoundaryEventState oldState,
      Variables variables) {
    FlowElementTrigger timeoutMessage =
        new FlowElementTrigger(
            processInstance.getProcessInstanceKey(), element.getId(), Constants.NONE, variables);
    List<SchedulableMessage<?>> timeoutMessages = List.of(timeoutMessage);
    Set<MessageScheduler> schedules =
        element.getTimerEventDefinitions().stream()
            .map(
                timerEventDefinition ->
                    messageSchedulerFactory.schedule(
                        processInstance.getProcessDefinitionKey(),
                        processInstance.getProcessInstanceKey(),
                        element.getId(),
                        timerEventDefinition,
                        timeoutMessages,
                        variables))
            .collect(Collectors.toSet());
    Set<ScheduleKey> scheduleKeys =
        schedules.stream().map(MessageScheduler::getScheduleKey).collect(Collectors.toSet());
    return TriggerResult.builder()
        .newFlowNodeState(
            new BoundaryEventState(
                oldState.getElementInstanceId(),
                oldState.getPassedCnt(),
                FlowNodeStateEnum.ACTIVE,
                scheduleKeys,
                oldState.getInputFlowId()))
        .messageSchedulers(schedules)
        .build();
  }

  @Override
  protected BoundaryEventState getTerminateElementState(BoundaryEventState elementState) {
    return new BoundaryEventState(
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        FlowNodeStateEnum.TERMINATED,
        elementState.getScheduleKeys(),
        elementState.getInputFlowId());
  }
}
