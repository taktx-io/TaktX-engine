package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.LinkEventDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.TerminateEventDefinition;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.state.ThrowEventState;

public abstract class ThrowEventProcessor<E extends ThrowEvent<?>, S extends ThrowEventState>
    extends EventProcessor<E, S> {

  @Override
  protected void triggerEvent(
      TriggerResultBuilder triggerResultBuilder,
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables) {
    List<ProcessInstanceTrigger> allTriggers = new ArrayList<>();

    element.getLinkventDefinitions().stream()
        .map(
            led ->
                getProcessInstanceTrigger(
                    processInstance.getProcessInstanceKey(), processDefinition, led, variables))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(allTriggers::add);

    element.getTerminateEventDefinitions().stream()
        .map(TerminateEventDefinition::getId)
        .map(ted -> getTerminateTrigger(processInstance.getProcessInstanceKey()))
        .forEach(allTriggers::add);

    triggerResultBuilder.processInstanceTriggers(allTriggers);

    triggerThrowEvent(
        triggerResultBuilder,
        trigger,
        processInstance,
        processDefinition,
        element,
        oldState,
        variables);
  }

  private ProcessInstanceTrigger getTerminateTrigger(ProcessInstanceKey processInstanceKey) {
    return new TerminateTrigger(processInstanceKey, Constants.NONE);
  }

  private Optional<ProcessInstanceTrigger> getProcessInstanceTrigger(
      ProcessInstanceKey processInstanceKey,
      ProcessDefinition processDefinition,
      LinkEventDefinition led,
      ScopedVars variables) {
    Optional<IntermediateCatchEvent> linkedCatchElement =
        processDefinition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getLinkedCatchElement(led.getName());
    return linkedCatchElement.map(
        catchEvent ->
            new FlowElementTrigger(
                processInstanceKey,
                catchEvent.getId(),
                Constants.NONE,
                variables.getCurrentScopeVariables()));
  }

  protected abstract void triggerThrowEvent(
      TriggerResultBuilder triggerResultBuilder,
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables);
}
