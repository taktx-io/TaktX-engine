package io.taktx.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BoundaryEventInstanceDTO extends CatchEventInstanceDTO {

  private long attachedInstanceId;
}
