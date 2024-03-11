package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
import nl.qunit.bpmnmeister.pi.state.SubProcessState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SubProcessProcessor extends ActivityProcessor<SubProcess, SubProcessState> {

  private static final Logger LOG = Logger.getLogger(SubProcessProcessor.class);

  @Override
  protected TriggerResult triggerWhenInit(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      SubProcess element,
      SubProcessState oldState,
      Variables variables) {
    Set<ProcessInstanceTrigger> subProcessTriggers = new HashSet<>();
    BaseElementId startElement = getStartEvent(element);

    ProcessInstanceTrigger subProcessTrigger =
        new ProcessInstanceTrigger(
            new ProcessInstanceKey(UUID.randomUUID()),
            processInstance.getProcessInstanceKey(),
            element.getAsSubProcessDefinition(processInstance.getProcessDefinition()),
            startElement,
            false,
            BaseElementId.NONE,
            trigger.getVariables());
    subProcessTriggers.add(subProcessTrigger);
    SubProcessState newSubProcessState =
        new SubProcessState(StateEnum.WAITING, oldState.getElementInstanceId());
    return new TriggerResult(
        newSubProcessState,
        Set.of(),
        Set.of(),
        subProcessTriggers,
        Variables.EMPTY);
  }

  @Override
  protected TriggerResult triggerWhenWaiting(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      SubProcess element,
      SubProcessState oldState,
      Variables variables) {
    SubProcessState newSubProcessState =
        new SubProcessState(StateEnum.FINISHED, oldState.getElementInstanceId());

    return new TriggerResult(
        newSubProcessState,
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        variables);
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
    return new SubProcessState(StateEnum.INIT, UUID.randomUUID());
  }

  @Override
  public SubProcessState terminate(SubProcessState oldState) {
    return new SubProcessState(StateEnum.TERMINATED, oldState.getElementInstanceId());
  }
}
