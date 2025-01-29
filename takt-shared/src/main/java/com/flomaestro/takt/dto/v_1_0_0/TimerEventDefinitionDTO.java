package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TimerEventDefinitionDTO extends EventDefinitionDTO {
  @JsonProperty("dt")
  private String timeDate;

  @JsonProperty("du")
  private String timeDuration;

  @JsonProperty("tc")
  private String timeCycle;

  public TimerEventDefinitionDTO(
      String id, String parentId, String timeDate, String timeDuration, String timeCycle) {
    super(id, parentId);
    this.timeDate = timeDate;
    this.timeDuration = timeDuration;
    this.timeCycle = timeCycle;
  }
}
