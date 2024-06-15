package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.SubProcess;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.SubProcessState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SubProcessProcessor extends ActivityProcessor<SubProcess, SubProcessState> {

  private static final Logger LOG = Logger.getLogger(SubProcessProcessor.class);

  @Override
  protected TriggerResult triggerFlowElementWithoutLoop(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      SubProcess element,
      SubProcessState oldState,
      ScopedVars variables) {
    if (oldState.getState() == FlowNodeStateEnum.READY) {
      return triggerWithoutLoopWhenReady(processInstance, definition, element, oldState, variables);
    } else if (oldState.getState() == FlowNodeStateEnum.ACTIVE) {
      return triggerWithoutLoopWhenActive(processInstance, definition, element, oldState);
    } else {
      LOG.warn("SubProcess is in state " + oldState.getState() + " and cannot be triggered.");
      return null;
    }
  }

  protected TriggerResult triggerWithoutLoopWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      SubProcess element,
      SubProcessState oldState,
      ScopedVars variables) {
    List<ProcessInstanceTrigger> subProcessTriggers = new ArrayList<>();
    String startElement = getStartEvent(element);
    ProcessInstanceKey childProcessInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
    ProcessInstanceKey parentProcessInstanceKey = processInstance.getProcessInstanceKey();
    variables.push(childProcessInstanceKey, parentProcessInstanceKey, Variables.empty());
    StartNewProcessInstanceTrigger subProcessTrigger =
        new StartNewProcessInstanceTrigger(
            childProcessInstanceKey,
            parentProcessInstanceKey,
            element.getAsSubProcessDefinition(definition),
            element.getId(),
            startElement,
            Constants.NONE,
            variables.getCurrentScopeVariables());
    subProcessTriggers.add(subProcessTrigger);
    SubProcessState newSubProcessState =
        new SubProcessState(
            FlowNodeStateEnum.ACTIVE,
            oldState.getElementInstanceId(),
            oldState.getPassedCnt(),
            oldState.getLoopCnt(),
            oldState.getInputFlowId());
    return TriggerResult.builder()
        .newFlowNodeState(newSubProcessState)
        .processInstanceTriggers(subProcessTriggers)
        .build();
  }

  protected TriggerResult triggerWithoutLoopWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition definition,
      SubProcess element,
      SubProcessState oldState) {
    SubProcessState newSubProcessState =
        new SubProcessState(
            FlowNodeStateEnum.FINISHED,
            oldState.getElementInstanceId(),
            oldState.getPassedCnt() + 1,
            oldState.getLoopCnt(),
            oldState.getInputFlowId());
    return finishActivity(
        processInstance, definition, element, newSubProcessState, ScopedVars.EMPTY);
  }

  private String getStartEvent(SubProcess subProcess) {
    if (!subProcess.getElements().getStartEvents().isEmpty()) {
      return subProcess.getElements().getStartEvents().get(0).getId();
    } else {
      return subProcess.getElements().values().get(0).getId();
    }
  }

  @Override
  protected SubProcessState getTerminateElementState(SubProcessState elementState) {
    return new SubProcessState(
        FlowNodeStateEnum.TERMINATED,
        elementState.getElementInstanceId(),
        elementState.getPassedCnt(),
        elementState.getLoopCnt(),
        elementState.getInputFlowId());
  }
}
