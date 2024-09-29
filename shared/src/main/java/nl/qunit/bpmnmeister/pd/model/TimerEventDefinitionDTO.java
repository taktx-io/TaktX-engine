package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class TimerEventDefinitionDTO extends EventDefinitionDTO {
  private final String timeDate;
  private final String timeDuration;
  private final String timeCycle;

  @JsonCreator
  public TimerEventDefinitionDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("timeDate") String timeDate,
      @Nonnull @JsonProperty("timeDuration") String timeDuration,
      @Nonnull @JsonProperty("timeCycle") String timeCycle) {
    super(id, parentId);
    this.timeDate = timeDate;
    this.timeDuration = timeDuration;
    this.timeCycle = timeCycle;
  }
}
