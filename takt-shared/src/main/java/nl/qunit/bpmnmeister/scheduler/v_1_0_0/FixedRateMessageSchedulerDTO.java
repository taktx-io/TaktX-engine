package nl.qunit.bpmnmeister.scheduler.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;

@Getter
@ToString
@EqualsAndHashCode
public class FixedRateMessageSchedulerDTO implements MessageSchedulerDTO {

  private final ProcessDefinitionKey processDefinitionKey;
  private final UUID processInstanceKey;
  private final String targetElementId;
  private final String timerDefinitionId;
  private final List<SchedulableMessageDTO<?>> messages;
  private final String period;
  private final int repetitions;
  private final int repeatedCnt;
  private final String instantiation;

  @JsonCreator
  public FixedRateMessageSchedulerDTO(
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @JsonProperty("targetElementId") String targetElementId,
      @JsonProperty("timerDefinitionId") String timerDefinitionId,
      @JsonProperty("messages") List<SchedulableMessageDTO<?>> messages,
      @JsonProperty("period") String period,
      @JsonProperty("repetitions") int repetitions,
      @JsonProperty("repeatedCnt") int repeatedCnt,
      @JsonProperty("instantiation") String instantiation) {
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
