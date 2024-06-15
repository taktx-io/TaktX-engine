package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
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

  public static List<ProcessInstanceTrigger> getSubProcessTriggersWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      ScopedVars variables,
      JsonNode inputCollection,
      int loopCnt) {
    return getSubProcessTrigger(
        processInstance, processDefinition, element, variables, inputCollection, loopCnt);
  }

  public static List<ProcessInstanceTrigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      ScopedVars variables,
      JsonNode inputCollection,
      int loopCnt) {
    return getSubProcessTrigger(
        processInstance, processDefinition, element, variables, inputCollection, loopCnt);
  }

  private static List<ProcessInstanceTrigger> getSubProcessTrigger(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      ScopedVars variables,
      JsonNode inputCollection,
      int loopCnt) {
    ProcessInstanceKey childProcessInstanceKey = new ProcessInstanceKey(UUID.randomUUID());
    ProcessInstanceKey parentProcessInstanceKey = processInstance.getProcessInstanceKey();
    variables.push(
        childProcessInstanceKey,
        parentProcessInstanceKey,
        Variables.of("loopCnt", new IntNode(loopCnt)));
    JsonNode inputElement = inputCollection.get(loopCnt);
    variables.put(element.getLoopCharacteristics().getInputElement(), inputElement);

    return List.of(
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
}
