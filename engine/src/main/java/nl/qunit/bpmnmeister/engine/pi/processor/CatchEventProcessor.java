package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.CatchEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.CatchEventState;

public abstract class CatchEventProcessor<E extends CatchEvent<?>, S extends CatchEventState>
    extends EventProcessor<E, S> {

  @Inject FeelExpressionHandler feelExpressionHandler;
  @Inject IoMappingProcessor ioMappingProcessor;

  @Override
  protected TriggerResult triggerEvent(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      Variables variables) {

    Variables outputVariables = ioMappingProcessor.getOutputVariables(element, variables);

    return triggerCatchEvent(
        trigger, processInstance, processDefinition, element, oldState, outputVariables);
  }

  protected abstract TriggerResult triggerCatchEvent(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      E element,
      S oldState,
      Variables variables);
}
