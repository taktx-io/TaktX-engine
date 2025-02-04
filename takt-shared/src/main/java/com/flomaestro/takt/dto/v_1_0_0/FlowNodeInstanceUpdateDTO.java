package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FlowNodeInstanceUpdateDTO extends InstanceUpdateDTO {

  @JsonProperty("ip")
  private List<Long> flowNodeInstancePath;

  @JsonProperty("i")
  private FlowNodeInstanceDTO flowNodeInstance;

  @JsonProperty("v")
  private VariablesDTO variables;

  @JsonProperty("t")
  private long processTime;

  public FlowNodeInstanceUpdateDTO(
      List<Long> flowNodeInstancePath,
      FlowNodeInstanceDTO flowNodeInstance,
      VariablesDTO variables,
      long processTime) {
    this.flowNodeInstancePath = flowNodeInstancePath;
    this.flowNodeInstance = flowNodeInstance;
    this.variables = variables;
    this.processTime = processTime;
  }

}
