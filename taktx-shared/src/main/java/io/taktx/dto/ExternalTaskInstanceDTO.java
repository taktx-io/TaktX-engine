package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

  @JsonProperty("at")
  private int attempt;

  @JsonProperty("k")
  private List<ScheduleKeyDTO> scheduledKeys;
}
