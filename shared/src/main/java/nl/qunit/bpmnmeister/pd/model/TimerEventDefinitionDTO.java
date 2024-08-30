package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import lombok.Getter;

@Getter
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    TimerEventDefinitionDTO that = (TimerEventDefinitionDTO) o;
    return Objects.equals(timeDate, that.timeDate)
        && Objects.equals(timeDuration, that.timeDuration)
        && Objects.equals(timeCycle, that.timeCycle);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), timeDate, timeDuration, timeCycle);
  }
}
