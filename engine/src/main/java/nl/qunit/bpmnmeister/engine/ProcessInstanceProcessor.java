package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.FlowElement;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.SequenceFlow;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstance;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.processor.ProcessorProvider;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.processor.StateProcessor;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.BpmnElementState;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;

@ApplicationScoped
public class ProcessInstanceProcessor {
  @Inject ProcessorProvider processorProvider;

  public Set<Trigger> trigger(
      Definitions processDefinition, ProcessInstance processInstance, Trigger trigger) {

    Optional<FlowElement> optFlowElement = processDefinition.getFlowElement(trigger.elementId());
    Set<Trigger> newTriggers = new HashSet<>();
    if (optFlowElement.isPresent()) {
      StateProcessor<?, ?> processor = processorProvider.getProcessor(optFlowElement.get());
      BpmnElementState elementState = processInstance.getElementStates().get(trigger.elementId());
      if (elementState == null) {
        elementState = processor.initialState();
      }
      TriggerResult triggerResult =
          processor.trigger(trigger, processDefinition, optFlowElement.get(), elementState);
      processInstance.getElementStates().put(trigger.elementId(), triggerResult.newElementState());
      triggerResult
          .newActiveFlows()
          .forEach(
              flowId -> {
                SequenceFlow flow =
                    (SequenceFlow) processDefinition.getFlowElement(flowId).orElseThrow();
                if (flow.testCondition()) {
                  newTriggers.add(
                      new Trigger(
                          processInstance.getProcessInstanceId(), flow.getTarget(), flow.getId()));
                }
              });
    }
    return newTriggers;
  }
}
