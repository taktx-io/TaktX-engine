/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
