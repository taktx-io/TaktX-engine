package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SubProcessInstanceDTO extends ActivityInstanceDTO implements WithFlowNodeInstancesDTO {

  @JsonProperty("f")
  private FlowNodeInstancesDTO flowNodeInstances;
}
