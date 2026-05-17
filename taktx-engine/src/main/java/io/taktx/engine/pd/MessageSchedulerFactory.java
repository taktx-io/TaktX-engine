/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import io.taktx.dto.FixedRateMessageScheduleDTO;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.OneTimeScheduleDTO;
import io.taktx.dto.RecurringMessageScheduleDTO;
import io.taktx.dto.SchedulableMessageDTO;
import io.taktx.dto.TimerEventDefinitionDTO;
import io.taktx.engine.feel.FeelExpressionHandlerImpl;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.proto.VariableValue;
import io.taktx.variables.Variables;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class MessageSchedulerFactory {
  @Inject FeelExpressionHandlerImpl feelExpressionHandler;

  public MessageScheduleDTO schedule(
      TimerEventDefinitionDTO timerEventDefinition,
      long now,
      SchedulableMessageDTO message,
      VariableScope variables) {
    if (timerEventDefinition.getTimeCycle() != null
        && !timerEventDefinition.getTimeCycle().isEmpty()) {
      return scheduleCycle(timerEventDefinition, message, variables, now);
    } else if (timerEventDefinition.getTimeDate() != null
        && !timerEventDefinition.getTimeDate().isEmpty()) {
      return scheduleOneTime(timerEventDefinition, message, variables, now);
    } else if (timerEventDefinition.getTimeDuration() != null
        && !timerEventDefinition.getTimeDuration().isEmpty()) {
      return scheduleDuration(timerEventDefinition, message, variables, now);
    }
    throw new IllegalArgumentException("TimerEventDefinition is not valid");
  }

  private MessageScheduleDTO scheduleDuration(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables,
      long now) {

    VariableValue durationValue =
        feelExpressionHandler.processFeelExpressionValue(
            timerEventDefinition.getTimeDuration(), variables);
    if (durationValue == null
        || durationValue.getKindCase() == VariableValue.KindCase.NULL_VALUE
        || durationValue.getKindCase() == VariableValue.KindCase.KIND_NOT_SET) {
      throw new IllegalArgumentException("TimeDuration expression returned null");
    }
    String timeDuration = String.valueOf(Variables.toJavaObject(durationValue));

    RepeatDuration repeatDuration = RepeatDuration.parse(timeDuration);
    Duration duration = repeatDuration.getDuration();

    return new OneTimeScheduleDTO(
        messages, now, Instant.ofEpochMilli(now).plus(duration).toEpochMilli());
  }

  private MessageScheduleDTO scheduleOneTime(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables,
      long now) {
    VariableValue timeDateValue =
        feelExpressionHandler.processFeelExpressionValue(
            timerEventDefinition.getTimeDate(), variables);
    if (timeDateValue == null
        || timeDateValue.getKindCase() == VariableValue.KindCase.NULL_VALUE
        || timeDateValue.getKindCase() == VariableValue.KindCase.KIND_NOT_SET) {
      throw new IllegalArgumentException("TimeDate expression returned null");
    }
    String timeDate = String.valueOf(Variables.toJavaObject(timeDateValue));
    return new OneTimeScheduleDTO(messages, now, Instant.parse(timeDate).toEpochMilli());
  }

  private MessageScheduleDTO scheduleCycle(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables,
      long now) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      return scheduleCron(timerEventDefinition, messages, variables, now);
    } else {
      return scheduleFixedRate(timerEventDefinition, messages, variables, now);
    }
  }

  private MessageScheduleDTO scheduleFixedRate(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables,
      long now) {

    VariableValue timeCycleValue =
        feelExpressionHandler.processFeelExpressionValue(
            timerEventDefinition.getTimeCycle(), variables);
    if (timeCycleValue == null
        || timeCycleValue.getKindCase() == VariableValue.KindCase.NULL_VALUE
        || timeCycleValue.getKindCase() == VariableValue.KindCase.KIND_NOT_SET) {
      throw new IllegalArgumentException("TimeCycle expression returned null");
    }

    String timeCycle = String.valueOf(Variables.toJavaObject(timeCycleValue));

    RepeatDuration repeatDuration = RepeatDuration.parse(timeCycle);
    return new FixedRateMessageScheduleDTO(
        messages, repeatDuration.getDuration().toMillis(), repeatDuration.getRepetitions(), now);
  }

  private MessageScheduleDTO scheduleCron(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables,
      long now) {
    VariableValue timeCycleValue =
        feelExpressionHandler.processFeelExpressionValue(
            timerEventDefinition.getTimeCycle(), variables);
    if (timeCycleValue == null
        || timeCycleValue.getKindCase() == VariableValue.KindCase.NULL_VALUE
        || timeCycleValue.getKindCase() == VariableValue.KindCase.KIND_NOT_SET) {
      throw new IllegalArgumentException("TimeCycle expression returned null");
    }
    String timeCycle = String.valueOf(Variables.toJavaObject(timeCycleValue));
    return new RecurringMessageScheduleDTO(messages, timeCycle, now);
  }

  private boolean isValidCron(String timeCycle) {

    // validate expression
    try {
      // get a predefined instanceToContinue
      CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);

      // create a parser based on provided definition
      CronParser parser = new CronParser(cronDefinition);
      parser.parse(timeCycle);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
