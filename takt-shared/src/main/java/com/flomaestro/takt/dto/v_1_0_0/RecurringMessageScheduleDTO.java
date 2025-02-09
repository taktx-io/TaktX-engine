package com.flomaestro.takt.dto.v_1_0_0;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RecurringMessageScheduleDTO extends MessageScheduleDTO {
  private static final CronDefinition CRON_DEFINITION = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
  private static final CronParser PARSER = new CronParser(CRON_DEFINITION);

  @JsonProperty("cron")
  private String cron;

  @JsonProperty("inst")
  private long instantiation;

  public RecurringMessageScheduleDTO(
      SchedulableMessageDTO messages, String cron, long instantiation) {
    super(messages);
    this.cron = cron;
    this.instantiation = instantiation;
  }

  @Override
  public TimeBucket getTimeBucket(long now) {
    Long firstNextExecutionTime = getNextExecutionTime(now);
    if (firstNextExecutionTime != null) {
      Long nextNextExecutionTime = getNextExecutionTime(firstNextExecutionTime);
      if (nextNextExecutionTime != null) {
        return TimeBucket.ofMillis(nextNextExecutionTime - firstNextExecutionTime);
      }
    }
    return null;
  }

  @Override
  public Long getNextExecutionTime(long timestamp) {
    Cron parsedCron = PARSER.parse(this.cron);
    ExecutionTime executionTime = ExecutionTime.forCron(parsedCron);
    Optional<ZonedDateTime> zonedDateTime =
        executionTime.nextExecution(ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
    if (zonedDateTime.isPresent()) {
      return zonedDateTime.get().toInstant().toEpochMilli();
    } else {
      return null;
    }
  }
}
