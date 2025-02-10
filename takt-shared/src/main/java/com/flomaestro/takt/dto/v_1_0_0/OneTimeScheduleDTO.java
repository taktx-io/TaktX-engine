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
public class OneTimeScheduleDTO extends MessageScheduleDTO {

  @JsonProperty("whn")
  private long when;

  public OneTimeScheduleDTO(SchedulableMessageDTO message, long when) {
    super(message);
    this.when = when;
  }

  @Override
  public TimeBucket getTimeBucket(long now) {
    return TimeBucket.ofMillis(when - now);
  }

  @Override
  public Long getNextExecutionTime(long timestamp) {
    if (when > timestamp) {
      return when;
    } else {
      return null;
    }
  }

}
