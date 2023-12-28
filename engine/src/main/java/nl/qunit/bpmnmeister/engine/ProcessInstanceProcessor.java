package nl.qunit.bpmnmeister.engine;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.Definitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.FlowElement;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.SequenceFlow;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ProcessInstance;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.Trigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;

@ApplicationScoped
public class ProcessInstanceProcessor {

  public Set<Trigger> trigger(
      Definitions processDefinition, ProcessInstance processInstance, Trigger trigger) {

    Optional<FlowElement> optFlowElement = processDefinition.getFlowElement(trigger.elementId());
    Set<Trigger> newTriggers = new HashSet<>();
    if (optFlowElement.isPresent()) {
      BpmnElementState elementState = processInstance.elementStates.get(trigger.elementId());
      TriggerResult triggerResult = optFlowElement.get().trigger(trigger, elementState);
      processInstance.elementStates.put(trigger.elementId(), triggerResult.newElementState());
      triggerResult
          .newActiveFlows()
          .forEach(
              flowId -> {
                SequenceFlow flow =
                    (SequenceFlow) processDefinition.getFlowElement(flowId).orElseThrow();
                if (Boolean.parseBoolean(flow.getCondition())) {
                  newTriggers.add(
                      new Trigger(
                          processInstance.processInstanceId, flow.getTarget(), flow.getId()));
                }
              });
    }

    //    triggerResult
    //        .externalTasks()
    //        .forEach(
    //            task ->
    //                externalTaskCOmmandEmitter.send(
    //                    new ExternalTaskCommand(task, processInstance.processInstanceId())));
    return newTriggers;
  }
}
