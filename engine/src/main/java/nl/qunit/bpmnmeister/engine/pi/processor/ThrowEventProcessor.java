package nl.qunit.bpmnmeister.engine.pi.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.IntermediateCatchEvent;
import nl.qunit.bpmnmeister.pd.model.LinkEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.TerminateEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ThrowEventDTO;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.TerminateTrigger;
import nl.qunit.bpmnmeister.pi.state.ThrowEventState;

public abstract class ThrowEventProcessor<E extends ThrowEventDTO<?>, S extends ThrowEventState>
    extends EventProcessor<E, S> {

  @Override
  protected void triggerEventStart(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO processDefinition,
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
        .map(TerminateEventDefinitionDTO::getId)
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

  @Override
  protected void triggerEventContinue(
      ContinueFlowElementTrigger continueFlowElementTrigger,
      TriggerResultBuilder triggerResultBuilder,
      ProcessInstance processInstance,
      ProcessDefinitionDTO processDefinition,
      E element,
      S oldState,
      ScopedVars variables) {
    throw new UnsupportedOperationException("Not implemented");
  }

  private ProcessInstanceTrigger getTerminateTrigger(UUID processInstanceKey) {
    return new TerminateTrigger(processInstanceKey, Constants.NONE, Constants.NONE_UUID);
  }

  private Optional<ProcessInstanceTrigger> getProcessInstanceTrigger(
      UUID processInstanceKey,
      ProcessDefinitionDTO processDefinition,
      LinkEventDefinitionDTO led,
      ScopedVars variables) {
    Optional<IntermediateCatchEvent> linkedCatchElement =
        processDefinition
            .getDefinitions()
            .getRootProcess()
            .getFlowElements()
            .getLinkedCatchElement(led.getName());
    return linkedCatchElement.map(
        catchEvent ->
            new StartFlowElementTrigger(
                processInstanceKey,
                Constants.NONE_UUID,
                catchEvent.getId(),
                Constants.NONE,
                variables.getCurrentScopeVariables()));
  }

  protected abstract void triggerThrowEvent(
      TriggerResultBuilder triggerResultBuilder,
      StartFlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO processDefinition,
      E element,
      S oldState,
      ScopedVars variables);
}
