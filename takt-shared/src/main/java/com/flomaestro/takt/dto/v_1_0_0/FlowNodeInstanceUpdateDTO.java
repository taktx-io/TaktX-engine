package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FlowNodeInstanceUpdateDTO extends InstanceUpdateDTO {
  @JsonProperty("fnii")
  private UUID flowNodeInstancesId;

  @JsonProperty("fni")
  private FlowNodeInstanceDTO flowNodeInstance;

  @JsonProperty("v")
  private VariablesDTO variables;

  public FlowNodeInstanceUpdateDTO(
      UUID processInstanceKey,
      UUID flowNodeInstancesId,
      FlowNodeInstanceDTO flowNodeInstance,
      VariablesDTO variables) {
    super(processInstanceKey);
    this.flowNodeInstancesId = flowNodeInstancesId;
    this.flowNodeInstance = flowNodeInstance;
    this.variables = variables;
  }
}
