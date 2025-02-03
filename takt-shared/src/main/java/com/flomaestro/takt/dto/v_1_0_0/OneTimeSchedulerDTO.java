package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.function.BiConsumer;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class OneTimeSchedulerDTO implements MessageSchedulerDTO {

  @JsonProperty("msgs")
  private SchedulableMessageDTO messages;

  @JsonProperty("whn")
  private String when;

  public OneTimeSchedulerDTO(SchedulableMessageDTO messages, String when) {
    this.messages = messages;
    this.when = when;
  }

  @Override
  public OneTimeSchedulerDTO evaluate(
      Instant now,
      ScheduleKeyDTO scheduleKey,
      BiConsumer<ScheduleKeyDTO, SchedulableMessageDTO> consumer) {
    if (Instant.parse(when).isBefore(now)) {
      // Time reached, return triggers
      consumer.accept(scheduleKey, messages);

      // Return null to indicate that this command is done
      return null;
    } else {
      // Time not yet reached, return this command
      return this;
    }
  }
}
