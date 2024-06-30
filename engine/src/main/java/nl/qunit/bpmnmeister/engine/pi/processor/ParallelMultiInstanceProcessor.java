package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;

class ParallelMultiInstanceProcessor {
  private ParallelMultiInstanceProcessor() {}

  public static List<ProcessInstanceTrigger> getSubProcessTriggersWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      ScopedVars variables,
      JsonNode inputCollection,
      int loopCnt) {

    List<ProcessInstanceTrigger> subProcessTriggers = new ArrayList<>();

    for (int i = 0; i < inputCollection.size(); i++) {
      UUID childProcessInstanceKey = UUID.randomUUID();
      UUID parentProcessInstanceKey = processInstance.getProcessInstanceKey();

      Variables childVariables = Variables.of("loopCnt", new IntNode(i));
      variables.push(childProcessInstanceKey, parentProcessInstanceKey, childVariables);
      JsonNode inputElement = inputCollection.get(i);
      variables.put(element.getLoopCharacteristics().getInputElement(), inputElement);
      subProcessTriggers.add(
          new StartNewProcessInstanceTrigger(
              processInstance.getRootInstanceKey(),
              childProcessInstanceKey,
              parentProcessInstanceKey,
              element.getAsSubProcessDefinition(processDefinition),
              element.getId(),
              element.getAsSubProcessStartElementId(),
              Constants.NONE,
              variables.getCurrentScopeVariables()));
    }
    return subProcessTriggers;
  }

  public static List<ProcessInstanceTrigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      ScopedVars variables,
      JsonNode inputCollection,
      int loopCnt) {
    return List.of();
  }
}
