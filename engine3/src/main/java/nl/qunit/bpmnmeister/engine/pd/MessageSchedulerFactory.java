package nl.qunit.bpmnmeister.engine.pd;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.feel.FeelExpressionHandlerImpl;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.TimerEventDefinitionDTO;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.scheduler.FixedRateMessageScheduler;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.RecurringMessageScheduler;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@ApplicationScoped
public class MessageSchedulerFactory {
  @Inject Clock clock;

  @Inject FeelExpressionHandlerImpl feelExpressionHandler;

  public MessageScheduler schedule(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessage<?>> messages,
      Variables2 variables) {
    if (timerEventDefinition.getTimeCycle() != null
        && !timerEventDefinition.getTimeCycle().isEmpty()) {
      return scheduleCycle(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages,
          variables);
    } else if (timerEventDefinition.getTimeDate() != null
        && !timerEventDefinition.getTimeDate().isEmpty()) {
      return scheduleOneTime(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages,
          variables);
    } else if (timerEventDefinition.getTimeDuration() != null
        && !timerEventDefinition.getTimeDuration().isEmpty()) {
      return scheduleDuration(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages,
          variables);
    }
    throw new IllegalArgumentException("TimerEventDefinition is not valid");
  }

  private MessageScheduler scheduleDuration(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessage<?>> messages,
      Variables2 variables) {

    String timeDuration =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeDuration(), variables)
            .asText();
    RepeatDuration repeatDuration = RepeatDuration.parse(timeDuration);
    Duration duration = Duration.parse(repeatDuration.getDuration());

    return new OneTimeScheduler(
        processDefinitionKey,
        processInstanceKey,
        targetElementId,
        timerEventDefinition.getId(),
        messages,
        Instant.now(clock).plus(duration).toString());
  }

  private MessageScheduler scheduleOneTime(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessage<?>> messages,
      Variables2 variables) {
    String timeDate =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeDate(), variables)
            .asText();
    return new OneTimeScheduler(
        processDefinitionKey,
        processInstanceKey,
        targetElementId,
        timerEventDefinition.getId(),
        messages,
        timeDate);
  }

  private MessageScheduler scheduleCycle(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessage<?>> messages,
      Variables2 variables) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      return scheduleCron(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages,
          variables);
    } else {
      return scheduleFixedRate(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages,
          variables);
    }
  }

  private MessageScheduler scheduleFixedRate(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessage<?>> messages,
      Variables2 variables) {

    String timeCycle =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeCycle(), variables)
            .asText();

    RepeatDuration repeatDuration = RepeatDuration.parse(timeCycle);
    Duration duration = Duration.parse(repeatDuration.getDuration());
    return new FixedRateMessageScheduler(
        processDefinitionKey,
        processInstanceKey,
        targetElementId,
        timerEventDefinition.getId(),
        messages,
        repeatDuration.getDuration(),
        repeatDuration.getRepetitions(),
        0,
        Instant.now(clock).plus(duration).toString());
  }

  private MessageScheduler scheduleCron(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessage<?>> messages,
      Variables2 variables) {
    String timeCycle =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeCycle(), variables)
            .asText();
    return new RecurringMessageScheduler(
        processDefinitionKey,
        processInstanceKey,
        targetElementId,
        timerEventDefinition.getId(),
        messages,
        timeCycle,
        Instant.now(clock).toString());
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
