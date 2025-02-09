package com.flomaestro.engine.pd;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.flomaestro.engine.feel.FeelExpressionHandlerImpl;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.FixedRateMessageScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.OneTimeScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.RecurringMessageScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.SchedulableMessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.TimerEventDefinitionDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class MessageSchedulerFactory {
  @Inject Clock clock;

  @Inject FeelExpressionHandlerImpl feelExpressionHandler;

  public MessageScheduleDTO schedule(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO message,
      VariableScope variables) {
    if (timerEventDefinition.getTimeCycle() != null
        && !timerEventDefinition.getTimeCycle().isEmpty()) {
      return scheduleCycle(timerEventDefinition, message, variables);
    } else if (timerEventDefinition.getTimeDate() != null
        && !timerEventDefinition.getTimeDate().isEmpty()) {
      return scheduleOneTime(timerEventDefinition, message, variables);
    } else if (timerEventDefinition.getTimeDuration() != null
        && !timerEventDefinition.getTimeDuration().isEmpty()) {
      return scheduleDuration(timerEventDefinition, message, variables);
    }
    throw new IllegalArgumentException("TimerEventDefinition is not valid");
  }

  private MessageScheduleDTO scheduleDuration(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables) {

    String timeDuration =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeDuration(), variables)
            .asText();
    RepeatDuration repeatDuration = RepeatDuration.parse(timeDuration);
    Duration duration = repeatDuration.getDuration();

    return new OneTimeScheduleDTO(messages, Instant.now(clock).plus(duration).toEpochMilli());
  }

  private MessageScheduleDTO scheduleOneTime(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables) {
    String timeDate =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeDate(), variables)
            .asText();
    return new OneTimeScheduleDTO(messages, Instant.parse(timeDate).toEpochMilli());
  }

  private MessageScheduleDTO scheduleCycle(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      return scheduleCron(timerEventDefinition, messages, variables);
    } else {
      return scheduleFixedRate(timerEventDefinition, messages, variables);
    }
  }

  private MessageScheduleDTO scheduleFixedRate(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables) {

    String timeCycle =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeCycle(), variables)
            .asText();

    RepeatDuration repeatDuration = RepeatDuration.parse(timeCycle);
    Duration duration = repeatDuration.getDuration();
    return new FixedRateMessageScheduleDTO(
        messages,
        repeatDuration.getDuration().toMillis(),
        repeatDuration.getRepetitions(),
        0,
        Instant.now(clock).plus(duration).toEpochMilli());
  }

  private MessageScheduleDTO scheduleCron(
      TimerEventDefinitionDTO timerEventDefinition,
      SchedulableMessageDTO messages,
      VariableScope variables) {
    String timeCycle =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeCycle(), variables)
            .asText();
    return new RecurringMessageScheduleDTO(messages, timeCycle, Instant.now(clock).toEpochMilli());
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
