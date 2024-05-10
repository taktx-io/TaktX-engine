package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.StartNewProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;

class ParallelMultiInstanceProcessor {
  private ParallelMultiInstanceProcessor() {}

  public static Set<ProcessInstanceTrigger> getSubProcessTriggersWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt) {

    Set<ProcessInstanceTrigger> subProcessTriggers = new HashSet<>();

    for (int i = 0; i < inputCollection.size(); i++) {
      Variables subProcessVariables = variables.put("loopCnt", new IntNode(i));
      JsonNode inputElement = inputCollection.get(i);
      subProcessVariables =
          subProcessVariables.put(element.getLoopCharacteristics().getInputElement(), inputElement);
      subProcessTriggers.add(
          new StartNewProcessInstanceTrigger(
              new ProcessInstanceKey(UUID.randomUUID()),
              processInstance.getProcessInstanceKey(),
              element.getAsSubProcessDefinition(processDefinition),
              element.getId(),
              element.getId(),
              Constants.NONE,
              subProcessVariables));
    }
    return subProcessTriggers;
  }

  public static Set<ProcessInstanceTrigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt) {
    return Set.of();
  }
}
