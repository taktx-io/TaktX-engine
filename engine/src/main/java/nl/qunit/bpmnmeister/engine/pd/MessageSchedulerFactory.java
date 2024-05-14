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
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.TimerEventDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.scheduler.FixedRateMessageScheduler;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.RecurringMessageScheduler;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@ApplicationScoped
public class MessageSchedulerFactory {
  @Inject Clock clock;

  public MessageScheduler schedule(
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstanceKey processInstanceKey,
      String targetElementId,
      TimerEventDefinition timerEventDefinition,
      List<SchedulableMessage<?>> messages) {
    if (timerEventDefinition.getTimeCycle() != null
        && !timerEventDefinition.getTimeCycle().isEmpty()) {
      return scheduleCycle(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages);
    } else if (timerEventDefinition.getTimeDate() != null
        && !timerEventDefinition.getTimeDate().isEmpty()) {
      return scheduleOneTime(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages);
    } else if (timerEventDefinition.getTimeDuration() != null
        && !timerEventDefinition.getTimeDuration().isEmpty()) {
      return scheduleDuration(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages);
    }
    throw new IllegalArgumentException("TimerEventDefinition is not valid");
  }

  private MessageScheduler scheduleDuration(
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstanceKey processInstanceKey,
      String targetElementId,
      TimerEventDefinition timerEventDefinition,
      List<SchedulableMessage<?>> messages) {

    RepeatDuration repeatDuration = RepeatDuration.parse(timerEventDefinition.getTimeDuration());
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

  private MessageScheduler scheduleOneTime(
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstanceKey processInstanceKey,
      String targetElementId,
      TimerEventDefinition timerEventDefinition,
      List<SchedulableMessage<?>> messages) {
    return new OneTimeScheduler(
        processDefinitionKey,
        processInstanceKey,
        targetElementId,
        timerEventDefinition.getId(),
        messages,
        timerEventDefinition.getTimeDate());
  }

  private MessageScheduler scheduleCycle(
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstanceKey processInstanceKey,
      String targetElementId,
      TimerEventDefinition timerEventDefinition,
      List<SchedulableMessage<?>> messages) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      return scheduleCron(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages);
    } else {
      return scheduleFixedRate(
          processDefinitionKey,
          processInstanceKey,
          targetElementId,
          timerEventDefinition,
          messages);
    }
  }

  private MessageScheduler scheduleFixedRate(
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstanceKey processInstanceKey,
      String targetElementId,
      TimerEventDefinition timerEventDefinition,
      List<SchedulableMessage<?>> messages) {

    RepeatDuration repeatDuration = RepeatDuration.parse(timerEventDefinition.getTimeCycle());
    return new FixedRateMessageScheduler(
        processDefinitionKey,
        processInstanceKey,
        targetElementId,
        timerEventDefinition.getId(),
        messages,
        repeatDuration.getDuration(),
        repeatDuration.getRepetitions(),
        0,
        Instant.now(clock).toString());
  }

  private MessageScheduler scheduleCron(
      ProcessDefinitionKey processDefinitionKey,
      ProcessInstanceKey processInstanceKey,
      String targetElementId,
      TimerEventDefinition timerEventDefinition,
      List<SchedulableMessage<?>> messages) {
    return new RecurringMessageScheduler(
        processDefinitionKey,
        processInstanceKey,
        targetElementId,
        timerEventDefinition.getId(),
        messages,
        timerEventDefinition.getTimeCycle(),
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
