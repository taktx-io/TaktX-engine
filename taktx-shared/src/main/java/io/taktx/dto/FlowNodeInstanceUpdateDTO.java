package io.taktx.dto;

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
  private List<Long> flowNodeInstancePath;

  private FlowNodeInstanceDTO flowNodeInstance;

  private VariablesDTO variables;

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
