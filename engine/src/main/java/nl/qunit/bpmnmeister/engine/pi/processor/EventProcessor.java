package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.inject.Inject;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult.TriggerResultBuilder;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.Event;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.EventState;

public abstract class EventProcessor<E extends Event<?>, S extends EventState>
    extends StateProcessor<E, S> {

  @Inject protected FeelExpressionHandler feelExpressionHandler;
  @Inject protected IoMappingProcessor ioMappingProcessor;

  @Override
  public TriggerResult triggerFlowElement(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      E element,
      S oldState,
      ScopedVars variables) {
    TriggerResultBuilder triggerResultBuilder = TriggerResult.builder();
    ProcessInstanceKey childProcessInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
    variables.push(
        childProcessInstanceKey, processInstance.getProcessInstanceKey(), trigger.getVariables());
    Variables outputVariables = ioMappingProcessor.getOutputVariables(element, variables);
    variables.pop();
    variables.merge(outputVariables);

    triggerEvent(
        triggerResultBuilder, trigger, processInstance, definition, element, oldState, variables);
    return triggerResultBuilder.build();
  }

  protected abstract void triggerEvent(
      TriggerResultBuilder triggerResultBuilder,
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      ScopedVars variables);
}
