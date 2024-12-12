package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TimerEventDefinitionDTO extends EventDefinitionDTO {
  @JsonProperty("dat")
  private String timeDate;

  @JsonProperty("dur")
  private String timeDuration;

  @JsonProperty("cyc")
  private String timeCycle;

  public TimerEventDefinitionDTO(
      String id, String parentId, String timeDate, String timeDuration, String timeCycle) {
    super(id, parentId);
    this.timeDate = timeDate;
    this.timeDuration = timeDuration;
    this.timeCycle = timeCycle;
  }
}
