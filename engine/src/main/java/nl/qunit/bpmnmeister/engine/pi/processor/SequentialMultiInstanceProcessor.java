package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
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

class SequentialMultiInstanceProcessor {
  private SequentialMultiInstanceProcessor() {}

  public static Set<ProcessInstanceTrigger> getSubProcessTriggersWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt) {
    return getSubProcessTrigger(
        processInstance, processDefinition, element, variables, inputCollection, loopCnt);
  }

  public static Set<ProcessInstanceTrigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt) {
    return getSubProcessTrigger(
        processInstance, processDefinition, element, variables, inputCollection, loopCnt);
  }

  private static Set<ProcessInstanceTrigger> getSubProcessTrigger(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt) {
    Variables updatedVariables = variables.put("loopCnt", new IntNode(loopCnt));
    JsonNode inputElement = inputCollection.get(loopCnt);
    updatedVariables =
        updatedVariables.put(element.getLoopCharacteristics().getInputElement(), inputElement);

    return java.util.Set.of(
        new StartNewProcessInstanceTrigger(
            new ProcessInstanceKey(UUID.randomUUID()),
            processInstance.getProcessInstanceKey(),
            element.getAsSubProcessDefinition(processDefinition),
            element.getId(),
            element.getAsSubProcessStartElementId(),
            Constants.NONE,
            updatedVariables));
  }
}
