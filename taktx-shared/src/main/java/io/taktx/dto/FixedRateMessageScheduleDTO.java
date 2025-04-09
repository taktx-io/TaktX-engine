package io.taktx.dto;

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

  public FixedRateMessageScheduleDTO(
      SchedulableMessageDTO message, long period, int repetitions, long instantiation) {
    super(message, instantiation);
    this.period = period;
    this.repetitions = repetitions;
  }

  @Override
  public Long getNextExecutionTime(long from) {
    if (period <= 0 || from < instantiationTime) {
      return null;
    }

    long nrOfFits = (from - instantiationTime) / period;
    if (repetitions < 2) {
      return instantiationTime + (nrOfFits + 1) * period;
    } else {
      if (nrOfFits < repetitions) {
        return instantiationTime + (nrOfFits + 1) * period;
      } else {
        return null;
      }
    }
  }
}
