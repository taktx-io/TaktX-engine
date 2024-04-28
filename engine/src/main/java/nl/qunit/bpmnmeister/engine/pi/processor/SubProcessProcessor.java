package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.ThrowingEvent;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.SubProcessState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SubProcessProcessor extends ActivityProcessor<SubProcess, SubProcessState> {

  private static final Logger LOG = Logger.getLogger(SubProcessProcessor.class);

  @Override
  protected TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      SubProcess element,
      SubProcessState oldState,
      Variables variables) {
    if (oldState.getState() == ActivityStateEnum.READY) {
      return triggerWhenReady(processInstance, element, oldState, variables);
    } else if (oldState.getState() == ActivityStateEnum.ACTIVE) {
      return triggerWhenActive(processInstance, element, oldState, variables);
    } else {
      LOG.warn("SubProcess is in state " + oldState.getState() + " and cannot be triggered.");
      return null;
    }
  }

  protected TriggerResult triggerWhenReady(
      ProcessInstance processInstance,
      SubProcess element,
      SubProcessState oldState,
      Variables variables) {
    Set<ProcessInstanceTrigger> subProcessTriggers = new HashSet<>();
    String startElement = getStartEvent(element);

    FlowElementTrigger subProcessTrigger =
        new FlowElementTrigger(
            new ProcessInstanceKey(UUID.randomUUID(), processInstance.getProcessInstanceKey()),
            processInstance.getProcessInstanceKey(),
            element.getId(),
            element.getAsSubProcessDefinition(processInstance.getProcessDefinition()),
            startElement,
            Constants.NONE,
            variables);
    subProcessTriggers.add(subProcessTrigger);
    SubProcessState newSubProcessState =
        new SubProcessState(
            ActivityStateEnum.ACTIVE, oldState.getElementInstanceId(), oldState.getPassedCnt());
    return new TriggerResult(
        newSubProcessState,
        Set.of(),
        Set.of(),
        subProcessTriggers,
        Set.of(),
        ThrowingEvent.NOOP,
        Variables.EMPTY);
  }

  protected TriggerResult triggerWhenActive(
      ProcessInstance processInstance,
      SubProcess element,
      SubProcessState oldState,
      Variables variables) {
    SubProcessState newSubProcessState =
        new SubProcessState(
            ActivityStateEnum.FINISHED,
            oldState.getElementInstanceId(),
            oldState.getPassedCnt() + 1);
    return finishActivity(processInstance, element, newSubProcessState, Variables.EMPTY);
  }

  private String getStartEvent(SubProcess subProcess) {
    if (!subProcess.getElements().getStartEvents().isEmpty()) {
      return subProcess.getElements().getStartEvents().get(0).getId();
    } else {
      return subProcess.getElements().values().get(0).getId();
    }
  }

  @Override
  public SubProcessState initialState() {
    return new SubProcessState(ActivityStateEnum.READY, UUID.randomUUID(), 0);
  }
}
