package io.taktx.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TimerEventDefinitionDTO extends EventDefinitionDTO {
  private String timeDate;

  private String timeDuration;

  private String timeCycle;

  public TimerEventDefinitionDTO(
      String id, String parentId, String timeDate, String timeDuration, String timeCycle) {
    super(id, parentId);
    this.timeDate = timeDate;
    this.timeDuration = timeDuration;
    this.timeCycle = timeCycle;
  }
}
