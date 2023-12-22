package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinition;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.SequenceFlow;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstance;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.Trigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;

@ApplicationScoped
public class ProcessInstanceProcessor {

  public Set<Trigger> trigger(
      ProcessDefinition processDefinition, ProcessInstance processInstance, Trigger trigger) {

    BpmnElement bpmnElement = processDefinition.bpmnElements.get(trigger.elementId());
    BpmnElementState elementState =
        processInstance.elementStates.computeIfAbsent(
            trigger.elementId(), id -> bpmnElement.createState());
    TriggerResult triggerResult = elementState.trigger(trigger, bpmnElement);

    Set<Trigger> newTriggers = new HashSet<>();
    processInstance.elementStates.put(trigger.elementId(), triggerResult.newElementState());
    //    triggerResult
    //        .externalTasks()
    //        .forEach(
    //            task ->
    //                externalTaskCOmmandEmitter.send(
    //                    new ExternalTaskCommand(task, processInstance.processInstanceId())));
    triggerResult
        .newActiveFlows()
        .forEach(
            flowId -> {
              SequenceFlow flow = processDefinition.flows.get(flowId);
              if (Boolean.parseBoolean(flow.getCondition())) {
                newTriggers.add(
                    new Trigger(processInstance.processInstanceId, flow.getTarget(), flow.getId()));
              }
            });
    return newTriggers;
  }
}
