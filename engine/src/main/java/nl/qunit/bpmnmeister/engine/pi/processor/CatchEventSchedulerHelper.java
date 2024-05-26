package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pd.MessageSchedulerFactory;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState;
import nl.qunit.bpmnmeister.pi.state.IntermediateCatchEventState.IntermediateCatchEventStateBuilder;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@ApplicationScoped
public class CatchEventSchedulerHelper {
  @Inject MessageSchedulerFactory messageSchedulerFactory;

  public void processWhenReady(
      TriggerResultBuilder triggerResultBuilder,
      IntermediateCatchEventStateBuilder<?, ?> newStateBuilder,
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

    newStateBuilder
        .state(FlowNodeStateEnum.ACTIVE)
        .scheduledKeys(
            messageSchedulers.stream()
                .map(MessageScheduler::getScheduleKey)
                .collect(Collectors.toSet()));

    triggerResultBuilder
        .messageSchedulers(messageSchedulers)
        .cancelSchedules(oldState.getScheduledKeys());
  }

  public void processWhenActive(
      FlowElementTrigger trigger,
      TriggerResultBuilder triggerResultBuilder,
      IntermediateCatchEventStateBuilder<?, ?> newStateBuilder,
      IntermediateCatchEvent element,
      IntermediateCatchEventState oldState) {
    if (trigger.getInputFlowId().equals(Constants.NONE)) {
      newStateBuilder.passedCnt(oldState.getPassedCnt() + 1);
      triggerResultBuilder.newActiveFlows(element.getOutgoing());
    }
  }
}
