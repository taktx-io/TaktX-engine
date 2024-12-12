package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
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
public class FixedRateMessageSchedulerDTO implements MessageSchedulerDTO {
  @JsonProperty("pdk")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("pik")
  private UUID processInstanceKey;

  @JsonProperty("tei")
  private String targetElementId;

  @JsonProperty("tedi")
  private String timerDefinitionId;

  @JsonProperty("msgs")
  private List<SchedulableMessageDTO<?>> messages;

  @JsonProperty("per")
  private String period;

  @JsonProperty("rep")
  private int repetitions;

  @JsonProperty("rc")
  private int repeatedCnt;

  @JsonProperty("inst")
  private String instantiation;

  public FixedRateMessageSchedulerDTO(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      String timerDefinitionId,
      List<SchedulableMessageDTO<?>> messages,
      String period,
      int repetitions,
      int repeatedCnt,
      String instantiation) {
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.targetElementId = targetElementId;
    this.timerDefinitionId = timerDefinitionId;
    this.messages = messages;
    this.period = period;
    this.repetitions = repetitions;
    this.repeatedCnt = repeatedCnt;
    this.instantiation = instantiation;
  }

  @Override
  public FixedRateMessageSchedulerDTO evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessageDTO<?>>> triggerConsumer) {
    Instant instant = Instant.parse(this.instantiation);
    if (now.isAfter(instant)) {
      // Time reached, return triggers
      triggerConsumer.accept(processInstanceKey, messages);

      if (repeatedCnt < (repetitions - 1) || repetitions < 0) {
        // Return a new command with the next execution time
        Instant nextExecution = instant.plus(Duration.parse(period));
        if (now.isAfter(nextExecution)) {
          // If the next execution time is already in the past, skip to the next one
          nextExecution = now.plus(Duration.parse(period));
        }
        return new FixedRateMessageSchedulerDTO(
            processDefinitionKey,
            processInstanceKey,
            targetElementId,
            timerDefinitionId,
            messages,
            period,
            repetitions,
            repeatedCnt + 1,
            nextExecution.toString());
      } else {
        // Return null to indicate that this command is done
        return null;
      }
    } else {
      // Time not yet reached, return this command
      return this;
    }
  }

  @Override
  public ScheduleType getScheduleType() {
    return ScheduleType.FIXED_RATE;
  }

  @Override
  public ScheduledKeyDTO getScheduledKey() {
    return new ScheduledKeyDTO(
        processDefinitionKey,
        processInstanceKey,
        ScheduleType.FIXED_RATE,
        targetElementId,
        timerDefinitionId);
  }
}
