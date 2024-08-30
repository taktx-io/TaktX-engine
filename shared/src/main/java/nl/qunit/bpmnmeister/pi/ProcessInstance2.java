package nl.qunit.bpmnmeister.pi;

import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
public class ProcessInstance2 {
  private final UUID processInstanceKey;
  private final ProcessDefinitionKey processDefinitionKey;
  private final FlowNodeStates2 flowNodeStates;

  public ProcessInstance2(
      UUID processInstanceKey,
      ProcessDefinitionKey processDefinitionKey,
      FlowNodeStates2 flowNodeStates) {
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeStates = flowNodeStates;
  }
}
