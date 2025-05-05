package io.taktx.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode
@ToString(callSuper = true)
@NoArgsConstructor
public class ExternalTaskMetaDTO {
  private String externalTasKId;
  private ExternalTaskMetaState state;
  private int nrPartitions;
}
