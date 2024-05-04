package nl.qunit.bpmnmeister.engine.pd;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.TimerEventDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.scheduler.FixedRateMessageScheduler;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.OneTimeScheduler;
import nl.qunit.bpmnmeister.scheduler.RecurringMessageScheduler;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;

@ApplicationScoped
public class StartCommandScheduler {
  @Inject Clock clock;
  @Inject FeelExpressionHandler feelExpressionHandler;

  public MessageScheduler schedule(
      ProcessDefinition processDefinition, TimerEventDefinition timerEventDefinition) {
    if (timerEventDefinition.getTimeCycle() != null) {
      return scheduleCycle(processDefinition, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDate() != null) {
      return scheduleOneTime(processDefinition, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDuration() != null) {
      return scheduleDuration(timerEventDefinition);
    }
    throw new IllegalArgumentException("TimerEventDefinition is not valid");
  }

  private MessageScheduler scheduleDuration(TimerEventDefinition timerEventDefinition) {
    return null;
  }

  private MessageScheduler scheduleOneTime(
      ProcessDefinition processDefinition, TimerEventDefinition timerEventDefinition) {
    List<SchedulableMessage<?>> startCommands = getStartCommands(processDefinition);

    return new OneTimeScheduler(startCommands, timerEventDefinition.getTimeDate());
  }

  private MessageScheduler scheduleCycle(
      ProcessDefinition processDefinition, TimerEventDefinition timerEventDefinition) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      return scheduleCron(processDefinition, timerEventDefinition);
    } else {
      return scheduleFixedRate(processDefinition, timerEventDefinition);
    }
  }

  private MessageScheduler scheduleFixedRate(
      ProcessDefinition processDefinition, TimerEventDefinition timerEventDefinition) {
    List<SchedulableMessage<?>> triggers = getStartCommands(processDefinition);

    RepeatDuration repeatDuration = RepeatDuration.parse(timerEventDefinition.getTimeCycle());
    return new FixedRateMessageScheduler(
        triggers,
        repeatDuration.getDuration(),
        repeatDuration.getRepetitions(),
        0,
        Instant.now(clock).toString());
  }

  private MessageScheduler scheduleCron(
      ProcessDefinition processDefinition, TimerEventDefinition timerEventDefinition) {
    List<SchedulableMessage<?>> messages = getStartCommands(processDefinition);
    return new RecurringMessageScheduler(
        messages, timerEventDefinition.getTimeCycle(), Instant.now(clock).toString());
  }

  private static List<SchedulableMessage<?>> getStartCommands(ProcessDefinition processDefinition) {
    List<SchedulableMessage<?>> processInstanceStartCommand = new ArrayList<>();
    processInstanceStartCommand.add(
        new StartCommand(
            ProcessInstanceKey.NONE,
            Constants.NONE,
            processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
            Variables.EMPTY));

    return processInstanceStartCommand;
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
