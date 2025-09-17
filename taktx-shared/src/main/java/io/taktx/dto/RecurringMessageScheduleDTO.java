/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.quarkus.runtime.annotations.RegisterForReflection;
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
@RegisterForReflection
public class RecurringMessageScheduleDTO extends MessageScheduleDTO {
  private static final CronDefinition CRON_DEFINITION =
      CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
  private static final CronParser PARSER = new CronParser(CRON_DEFINITION);

  private String cron;

  public RecurringMessageScheduleDTO(
      SchedulableMessageDTO message, String cron, long instantiation) {
    super(message, instantiation);
    this.cron = cron;
  }

  @Override
  public Long getNextExecutionTime(long timestamp) {
    Cron parsedCron = PARSER.parse(this.cron);
    ExecutionTime executionTime = ExecutionTime.forCron(parsedCron);
    Optional<ZonedDateTime> zonedDateTime =
        executionTime.nextExecution(
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
    if (zonedDateTime.isPresent()) {
      return zonedDateTime.get().toInstant().toEpochMilli();
    } else {
      return null;
    }
  }
}
