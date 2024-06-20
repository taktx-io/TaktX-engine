package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.Optional;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.LinkEventDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ThrowEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
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
    Set<LinkEventDefinition> linkEventDefinitions = element.getLinkventDefinitions();

    triggerResultBuilder.processInstanceTriggers(
        linkEventDefinitions.stream()
            .map(
                led ->
                    getProcessInstanceTrigger(
                        processInstance.getProcessInstanceKey(), processDefinition, led, variables))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList());

    triggerThrowEvent(
        triggerResultBuilder,
        trigger,
        processInstance,
        processDefinition,
        element,
        oldState,
        variables);
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
