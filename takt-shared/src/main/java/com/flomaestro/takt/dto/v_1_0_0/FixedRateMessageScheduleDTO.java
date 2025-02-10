package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FixedRateMessageScheduleDTO extends MessageScheduleDTO {

  @JsonProperty("per")
  private long period;

  @JsonProperty("rep")
  private int repetitions;

  @JsonProperty("rc")
  private int repeatedCnt;

  @JsonProperty("inst")
  private long instantiation;

  public FixedRateMessageScheduleDTO(
      SchedulableMessageDTO message,
      long period,
      int repetitions,
      int repeatedCnt,
      long instantiation) {
    super(message);
    this.period = period;
    this.repetitions = repetitions;
    this.repeatedCnt = repeatedCnt;
    this.instantiation = instantiation;
  }

  @Override
  public TimeBucket getTimeBucket(long millis) {
    return TimeBucket.ofMillis(period);
  }

  @Override
  public Long getNextExecutionTime(long from) {
    if (repeatedCnt < (repetitions - 1) || repetitions < 0) {
      long nrOfFits = (from - instantiation) / period;
      return instantiation + (nrOfFits + 1) * period;
    } else {
      // Return null to indicate that this command is done
      return null;
    }
  }
}
