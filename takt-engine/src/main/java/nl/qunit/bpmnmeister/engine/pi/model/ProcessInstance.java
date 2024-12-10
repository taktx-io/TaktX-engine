package nl.qunit.bpmnmeister.engine.pi.model;

import java.util.List;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;

@Getter
public class ProcessInstance {
  private final UUID processInstanceKey;
  private final UUID parentProcessInstanceKey;
  private final List<String> parentElementIdPath;
  private final List<UUID> parentElementInstancePath;
  private final ProcessDefinitionKey processDefinitionKey;
  private final FlowNodeInstances flowNodeInstances;

  public ProcessInstance(
      UUID processInstanceKey,
      UUID parentProcessInstanceKey,
      List<String> parentElementIdPath,
      List<UUID> parentElementInstancePath,
      ProcessDefinitionKey processDefinitionKey,
      FlowNodeInstances flowNodeInstances) {
    this.processInstanceKey = processInstanceKey;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeInstances = flowNodeInstances;
  }
}
