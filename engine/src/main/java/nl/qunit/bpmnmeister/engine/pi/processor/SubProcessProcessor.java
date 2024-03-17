package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pi.FlowElementNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Trigger;
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
      return triggerWhenReady(trigger, processInstance, element, oldState, variables);
    } else if (oldState.getState() == ActivityStateEnum.ACTIVE) {
      return triggerWhenActive(trigger, processInstance, element, oldState, variables);
    } else {
      LOG.warn("SubProcess is in state " + oldState.getState() + " and cannot be triggered.");
      return null;
    }
  }

  protected TriggerResult triggerWhenReady(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      SubProcess element,
      SubProcessState oldState,
      Variables variables) {
    Set<Trigger> subProcessTriggers = new HashSet<>();
    BaseElementId startElement = getStartEvent(element);

    FlowElementNewProcessInstanceTrigger subProcessTrigger =
        new FlowElementNewProcessInstanceTrigger(
            new ProcessInstanceKey(UUID.randomUUID()),
            processInstance.getProcessInstanceKey(),
            element.getAsSubProcessDefinition(processInstance.getProcessDefinition()),
            startElement,
            variables);
    subProcessTriggers.add(subProcessTrigger);
    SubProcessState newSubProcessState =
        new SubProcessState(ActivityStateEnum.ACTIVE, oldState.getElementInstanceId());
    return new TriggerResult(
        newSubProcessState, Set.of(), Set.of(), subProcessTriggers, Variables.EMPTY);
  }

  protected TriggerResult triggerWhenActive(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      SubProcess element,
      SubProcessState oldState,
      Variables variables) {
    SubProcessState newSubProcessState =
        new SubProcessState(ActivityStateEnum.FINISHED, oldState.getElementInstanceId());

    return new TriggerResult(
        newSubProcessState, element.getOutgoing(), Set.of(), Set.of(), variables);
  }

  private BaseElementId getStartEvent(SubProcess subProcess) {
    if (!subProcess.getElements().getStartEvents().isEmpty()) {
      return subProcess.getElements().getStartEvents().get(0).getId();
    } else {
      return subProcess.getElements().values().get(0).getId();
    }
  }

  @Override
  public SubProcessState initialState() {
    return new SubProcessState(ActivityStateEnum.READY, UUID.randomUUID());
  }
}
