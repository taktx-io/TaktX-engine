package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

  @JsonProperty("pdk")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("pik")
  private UUID processInstanceKey;

  @JsonProperty("tei")
  private String targetElementId;

  @JsonProperty("tedi")
  private String timerEventDefinitionId;

  @JsonProperty("msgs")
  private List<SchedulableMessageDTO<?>> messages;

  @JsonProperty("whn")
  private String when;

  public OneTimeSchedulerDTO(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      String timerEventDefinitionId,
      List<SchedulableMessageDTO<?>> messages,
      String when) {
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.targetElementId = targetElementId;
    this.timerEventDefinitionId = timerEventDefinitionId;
    this.messages = messages;
    this.when = when;
  }

  @Override
  public OneTimeSchedulerDTO evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessageDTO<?>>> consumer) {
    if (Instant.parse(when).isBefore(now)) {
      // Time reached, return triggers
      consumer.accept(processInstanceKey, messages);

      // Return null to indicate that this command is done
      return null;
    } else {
      // Time not yet reached, return this command
      return this;
    }
  }

  @Override
  public ScheduleType getScheduleType() {
    return ScheduleType.ONE_TIME;
  }

  @Override
  public ScheduledKeyDTO getScheduledKey() {
    return new ScheduledKeyDTO(
        processDefinitionKey,
        processInstanceKey,
        ScheduleType.ONE_TIME,
        targetElementId,
        timerEventDefinitionId);
  }
}
