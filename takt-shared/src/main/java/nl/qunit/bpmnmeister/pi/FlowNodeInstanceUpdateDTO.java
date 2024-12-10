package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FlowNodeInstanceUpdateDTO extends InstanceUpdateDTO {

  private final UUID flowNodeInstancesId;
  private final FlowNodeInstanceDTO flowNodeInstance;
  private final VariablesDTO variables;

  @JsonCreator
  public FlowNodeInstanceUpdateDTO(
      @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @JsonProperty("flowNodeInstancesId") UUID flowNodeInstancesId,
      @JsonProperty("flowNodeInstance") FlowNodeInstanceDTO flowNodeInstance,
      @JsonProperty("variables") VariablesDTO variables) {
    super(processInstanceKey);
    this.flowNodeInstancesId = flowNodeInstancesId;
    this.flowNodeInstance = flowNodeInstance;
    this.variables = variables;
  }
}
