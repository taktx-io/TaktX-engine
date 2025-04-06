/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pd;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import io.taktx.dto.v_1_0_0.FixedRateMessageScheduleDTO;
import io.taktx.dto.v_1_0_0.MessageScheduleDTO;
import io.taktx.dto.v_1_0_0.OneTimeScheduleDTO;
import io.taktx.dto.v_1_0_0.RecurringMessageScheduleDTO;
import io.taktx.dto.v_1_0_0.SchedulableMessageDTO;
import io.taktx.dto.v_1_0_0.TimerEventDefinitionDTO;
import io.taktx.engine.feel.FeelExpressionHandlerImpl;
import io.taktx.engine.pi.model.VariableScope;
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

    String timeDuration =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeDuration(), variables)
            .asText();
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
    String timeDate =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeDate(), variables)
            .asText();
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

    String timeCycle =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeCycle(), variables)
            .asText();

    RepeatDuration repeatDuration = RepeatDuration.parse(timeCycle);
    return new FixedRateMessageScheduleDTO(
        messages, repeatDuration.getDuration().toMillis(), repeatDuration.getRepetitions(), now);
  }

  private MessageScheduleDTO scheduleCron(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables,
      long now) {
    String timeCycle =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeCycle(), variables)
            .asText();
    return new RecurringMessageScheduleDTO(messages, timeCycle, now);
  }

  private boolean isValidCron(String timeCycle) {

    // validate expression
    try {
      // get a predefined instance
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
