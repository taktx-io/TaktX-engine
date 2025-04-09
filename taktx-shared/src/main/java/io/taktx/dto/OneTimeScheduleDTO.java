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
public class OneTimeScheduleDTO extends MessageScheduleDTO {

  @JsonProperty("whn")
  private long when;

  public OneTimeScheduleDTO(SchedulableMessageDTO message, long instantiationTime, long when) {
    super(message, instantiationTime);
    this.when = when;
  }

  @Override
  public Long getNextExecutionTime(long timestamp) {
    if (timestamp >= when) {
      return null;
    }
    return when;
  }
}
