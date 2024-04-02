package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Trigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.MultiInstanceState;

@ApplicationScoped
public class SequentialMultiInstanceProcessor extends MultiInstanceProcessor {
  @Override
  protected Set<Trigger> getSubProcessTriggersWhenReady(
      ProcessInstance processInstance,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt) {
    return getSubProcessTrigger(processInstance, element, variables, loopCnt);
  }

  @Override
  protected Set<Trigger> getSubProcessTriggersWhenActive(
      ProcessInstance processInstance,
      Activity element,
      Variables variables,
      JsonNode inputCollection,
      int loopCnt) {
    return getSubProcessTrigger(processInstance, element, variables, loopCnt);
  }

  private static Set<Trigger> getSubProcessTrigger(
      ProcessInstance processInstance, Activity element, Variables variables, int loopCnt) {
    Variables updatedVariables = variables.put("loopCnt", new IntNode(loopCnt));

    return Set.of(
        new FlowElementTrigger(
            new ProcessInstanceKey(UUID.randomUUID(), processInstance.getProcessInstanceKey()),
            processInstance.getProcessInstanceKey(),
            element.getAsSubProcessDefinition(processInstance.getProcessDefinition()),
            element.getId(),
            BaseElementId.NONE,
            updatedVariables));
  }

  @Override
  public MultiInstanceState initialState() {
    return new MultiInstanceState(ActivityStateEnum.READY, UUID.randomUUID(), 0, 0);
  }
}
