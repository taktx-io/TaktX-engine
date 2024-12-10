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
import nl.qunit.bpmnmeister.engine.pi.model.Variables;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.TimerEventDefinitionDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.FixedRateMessageSchedulerDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.MessageSchedulerDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.OneTimeSchedulerDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.RecurringMessageSchedulerDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.SchedulableMessageDTO;

@ApplicationScoped
public class MessageSchedulerFactory {
  @Inject Clock clock;

  @Inject FeelExpressionHandlerImpl feelExpressionHandler;

  public MessageSchedulerDTO schedule(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessageDTO<?>> messages,
      Variables variables) {
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

  private MessageSchedulerDTO scheduleDuration(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessageDTO<?>> messages,
      Variables variables) {

    String timeDuration =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeDuration(), variables)
            .asText();
    RepeatDuration repeatDuration = RepeatDuration.parse(timeDuration);
    Duration duration = Duration.parse(repeatDuration.getDuration());

    return new OneTimeSchedulerDTO(
        processDefinitionKey,
        processInstanceKey,
        targetElementId,
        timerEventDefinition.getId(),
        messages,
        Instant.now(clock).plus(duration).toString());
  }

  private MessageSchedulerDTO scheduleOneTime(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessageDTO<?>> messages,
      Variables variables) {
    String timeDate =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeDate(), variables)
            .asText();
    return new OneTimeSchedulerDTO(
        processDefinitionKey,
        processInstanceKey,
        targetElementId,
        timerEventDefinition.getId(),
        messages,
        timeDate);
  }

  private MessageSchedulerDTO scheduleCycle(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessageDTO<?>> messages,
      Variables variables) {
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

  private MessageSchedulerDTO scheduleFixedRate(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessageDTO<?>> messages,
      Variables variables) {

    String timeCycle =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeCycle(), variables)
            .asText();

    RepeatDuration repeatDuration = RepeatDuration.parse(timeCycle);
    Duration duration = Duration.parse(repeatDuration.getDuration());
    return new FixedRateMessageSchedulerDTO(
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

  private MessageSchedulerDTO scheduleCron(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      String targetElementId,
      TimerEventDefinitionDTO timerEventDefinition,
      List<SchedulableMessageDTO<?>> messages,
      Variables variables) {
    String timeCycle =
        feelExpressionHandler
            .processFeelExpression(timerEventDefinition.getTimeCycle(), variables)
            .asText();
    return new RecurringMessageSchedulerDTO(
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
