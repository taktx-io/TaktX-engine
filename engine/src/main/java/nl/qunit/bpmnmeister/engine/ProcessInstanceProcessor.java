package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.model.processdefinition.*;
import nl.qunit.bpmnmeister.model.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.model.processinstance.ProcessInstance;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;
import nl.qunit.bpmnmeister.model.processinstance.TriggerResult;

@ApplicationScoped
public class ProcessInstanceProcessor {

  public Set<Trigger> trigger(
      ProcessDefinition processDefinition, ProcessInstance processInstance, Trigger trigger) {
    BpmnElement bpmnElement = processDefinition.bpmnElements().get(trigger.elementId());
    BpmnElementState elementState =
        processInstance
            .elementStates()
            .computeIfAbsent(trigger.elementId(), id -> bpmnElement.createState());
    TriggerResult triggerResult = elementState.trigger(trigger, bpmnElement);

    Set<Trigger> newTriggers = new HashSet<>();
    processInstance.elementStates().put(trigger.elementId(), triggerResult.newState());
    triggerResult
        .newActiveFlows()
        .forEach(
            flowId -> {
              BpmnFlow flow = processDefinition.flows().get(flowId);
              if (flow.condition().test(processInstance)) {
                newTriggers.add(new Trigger(flow.target(), flow.id()));
              }
            });
    return newTriggers;
  }
}
