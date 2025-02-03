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
import java.util.Optional;
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

  @JsonProperty("msgs")
  private SchedulableMessageDTO message;

  @JsonProperty("cron")
  private String cron;

  @JsonProperty("inst")
  private String instantiation;

  public RecurringMessageSchedulerDTO(
      SchedulableMessageDTO message, String cron, String instantiation) {
    this.message = message;
    this.cron = cron;
    this.instantiation = instantiation;
  }

  @Override
  public RecurringMessageSchedulerDTO evaluate(
      Instant now,
      ScheduleKeyDTO scheduleKey,
      BiConsumer<ScheduleKeyDTO, SchedulableMessageDTO> triggerConsumer) {
    CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
    CronParser parser = new CronParser(cronDefinition);
    Cron parsedCron = parser.parse(this.cron);
    ExecutionTime executionTime = ExecutionTime.forCron(parsedCron);
    Optional<ZonedDateTime> zonedDateTime =
        executionTime.nextExecution(ZonedDateTime.parse(instantiation));
    if (zonedDateTime.isPresent()) {
      if (now.isAfter(zonedDateTime.get().toInstant())) {
        // Time reached, return triggers
        triggerConsumer.accept(scheduleKey, message);

        // Return a new command with the next execution time
        return new RecurringMessageSchedulerDTO(
            message, parsedCron.asString(), zonedDateTime.get().toString());
      } else {
        // Time not yet reached, return this command
        return this;
      }
    }
    // Something went wrong, return null to indicate that this command is done
    return null;
  }
}
