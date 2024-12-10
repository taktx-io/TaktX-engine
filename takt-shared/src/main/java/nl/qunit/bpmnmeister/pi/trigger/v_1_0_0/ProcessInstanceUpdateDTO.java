package nl.qunit.bpmnmeister.pi.trigger.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.FlowNodeInstancesDTO;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ProcessInstanceUpdateDTO extends InstanceUpdateDTO {
  private final UUID parentProcessInstanceKey;
  private final List<String> parentElementIdPath;
  private final List<UUID> parentElementInstancePath;
  private final ProcessDefinitionKey processDefinitionKey;
  private final FlowNodeInstancesDTO flowNodeInstances;
  private final VariablesDTO variables;

  @JsonCreator
  public ProcessInstanceUpdateDTO(
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("parentProcessInstanceKey") UUID parentProcessInstanceKey,
      @Nonnull @JsonProperty("parentElementIdPath") List<String> parentElementIdPath,
      @Nonnull @JsonProperty("parentElementInstancePath") List<UUID> parentElementInstancePath,
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("flowNodeInstances") FlowNodeInstancesDTO flowNodeInstances,
      @Nonnull @JsonProperty("variables") VariablesDTO variables) {
    super(processInstanceKey);
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementIdPath = parentElementIdPath;
    this.parentElementInstancePath = parentElementInstancePath;
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeInstances = flowNodeInstances;
    this.variables = variables;
  }

  public ProcessInstanceUpdateDTO(ProcessInstanceDTO processInstance, VariablesDTO variables) {
    this(
        processInstance.getProcessInstanceKey(),
        processInstance.getParentProcessInstanceKey(),
        processInstance.getParentElementIdPath(),
        processInstance.getParentElementInstancePath(),
        processInstance.getProcessDefinitionKey(),
        processInstance.getFlowNodeInstances(),
        variables);
  }
}
