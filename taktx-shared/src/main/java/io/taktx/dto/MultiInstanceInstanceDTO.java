package io.taktx.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MultiInstanceInstanceDTO extends ActivityInstanceDTO
    implements WithFlowNodeInstancesDTO {
  private FlowNodeInstancesDTO flowNodeInstances;
}
