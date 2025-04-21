package io.taktx.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SubProcessInstanceDTO extends ActivityInstanceDTO implements WithFlowNodeInstancesDTO {

  private FlowNodeInstancesDTO flowNodeInstances;
}
