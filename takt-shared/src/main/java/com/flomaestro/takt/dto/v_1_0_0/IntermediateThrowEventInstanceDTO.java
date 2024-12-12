package com.flomaestro.takt.dto.v_1_0_0;

import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IntermediateThrowEventInstanceDTO extends ThrowEventInstanceDTO {

  public IntermediateThrowEventInstanceDTO(
      UUID elementInstanceId, String elementId, int passedCnt) {
    super(elementInstanceId, elementId, passedCnt);
  }
}
