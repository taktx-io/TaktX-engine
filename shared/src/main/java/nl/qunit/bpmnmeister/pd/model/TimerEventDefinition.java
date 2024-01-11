package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class TimerEventDefinition extends EventDefinition {
  private final String timeDate;
  private final String timeDuration;
  private final String timeCycle;

  @JsonCreator
  public TimerEventDefinition(
      @JsonProperty("id") String id,
      @JsonProperty("timeDate") String timeDate,
      @JsonProperty("timeDuration") String timeDuration,
      @JsonProperty("timeCycle") String timeCycle) {
    super(id);
    this.timeDate = timeDate;
    this.timeDuration = timeDuration;
    this.timeCycle = timeCycle;
  }
}
