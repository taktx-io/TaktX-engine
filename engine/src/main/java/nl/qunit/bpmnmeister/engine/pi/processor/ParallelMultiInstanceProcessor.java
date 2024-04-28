package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.MultiInstanceState;

@ApplicationScoped
public class ParallelMultiInstanceProcessor extends MultiInstanceProcessor {

  @Override
  protected Set<ProcessInstanceTrigger> getSubProcessTriggersWhenReady(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt) {
    ProcessDefinition subProcessDefinition = element.getAsSubProcessDefinition(processDefinition);

    Set<ProcessInstanceTrigger> subProcessTriggers = new HashSet<>();

    for (int i = 0; i < inputCollection.size(); i++) {
      Variables subProcessVariables = variables.put("loopCnt", new IntNode(i));
      JsonNode inputElement = inputCollection.get(i);
      subProcessVariables =
          subProcessVariables.put(element.getLoopCharacteristics().getInputElement(), inputElement);
      subProcessTriggers.add(
          new FlowElementTrigger(
              new ProcessInstanceKey(UUID.randomUUID(), processInstance.getProcessInstanceKey()),
              processInstance.getProcessInstanceKey(),
              element.getId(),
              subProcessDefinition,
              element.getId(),
              Constants.NONE,
              subProcessVariables));
    }
    return subProcessTriggers;
  }

  @Override
  protected Set<ProcessInstanceTrigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      ProcessDefinition processDefinition,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt) {
    return Set.of();
  }

  @Override
  public MultiInstanceState initialState() {
    return new MultiInstanceState(ActivityStateEnum.READY, UUID.randomUUID(), 0, 0);
  }
}
