package com.flomaestro.takt.dto.v_1_0_0;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
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
public class RecurringMessageSchedulerDTO implements MessageSchedulerDTO {

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

  @JsonProperty("cron")
  private String cron;

  @JsonProperty("inst")
  private String instantiation;

  public RecurringMessageSchedulerDTO(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      String timerEventDefinitionId,
      List<SchedulableMessageDTO<?>> messages,
      String cron,
      String instantiation) {
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.targetElementId = targetElementId;
    this.timerEventDefinitionId = timerEventDefinitionId;
    this.messages = messages;
    this.cron = cron;
    this.instantiation = instantiation;
  }

  @Override
  public RecurringMessageSchedulerDTO evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessageDTO<?>>> triggerConsumer) {
    CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
    CronParser parser = new CronParser(cronDefinition);
    Cron parsedCron = parser.parse(this.cron);
    ExecutionTime executionTime = ExecutionTime.forCron(parsedCron);
    Optional<ZonedDateTime> zonedDateTime =
        executionTime.nextExecution(ZonedDateTime.parse(instantiation));
    if (zonedDateTime.isPresent()) {
      if (now.isAfter(zonedDateTime.get().toInstant())) {
        // Time reached, return triggers
        triggerConsumer.accept(processInstanceKey, messages);

        // Return a new command with the next execution time
        return new RecurringMessageSchedulerDTO(
            processDefinitionKey,
            processInstanceKey,
            targetElementId,
            timerEventDefinitionId,
            messages,
            parsedCron.asString(),
            zonedDateTime.get().toString());
      } else {
        // Time not yet reached, return this command
        return this;
      }
    }
    // Something went wrong, return null to indicate that this command is done
    return null;
  }

  @Override
  public ScheduleType getScheduleType() {
    return ScheduleType.RECURRING;
  }

  @Override
  public ScheduledKeyDTO getScheduledKey() {
    return new ScheduledKeyDTO(
        processDefinitionKey,
        processInstanceKey,
        ScheduleType.RECURRING,
        targetElementId,
        timerEventDefinitionId);
  }
}
