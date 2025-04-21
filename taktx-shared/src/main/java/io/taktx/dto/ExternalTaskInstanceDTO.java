package io.taktx.dto;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public abstract class ExternalTaskInstanceDTO extends TaskInstanceDTO {

  private int attempt;

  private List<ScheduleKeyDTO> scheduledKeys;
}
