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
import nl.qunit.bpmnmeister.pd.model.SequenceFlow;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pd.model.TimerEventDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.scheduler.FixedRateStartCommand;
import nl.qunit.bpmnmeister.scheduler.OneTimeStartCommand;
import nl.qunit.bpmnmeister.scheduler.RecurringStartCommand;
import nl.qunit.bpmnmeister.scheduler.RepeatDuration;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduleCommand;

@ApplicationScoped
public class ScheduleCommandFactory {
  @Inject Clock clock;
  @Inject FeelExpressionHandler feelExpressionHandler;

  public ScheduleCommand schedule(
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    if (timerEventDefinition.getTimeCycle() != null) {
      return scheduleCycle(processDefinition, startEvent, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDate() != null) {
      return scheduleOneTime(processDefinition, startEvent, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDuration() != null) {
      return scheduleDuration(timerEventDefinition);
    }
    throw new IllegalArgumentException("TimerEventDefinition is not valid");
  }

  private ScheduleCommand scheduleDuration(TimerEventDefinition timerEventDefinition) {
    return null;
  }

  private ScheduleCommand scheduleOneTime(
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<SchedulableMessage> startCommands = getStartCommands(processDefinition, startEvent);

    return new OneTimeStartCommand(startCommands, timerEventDefinition.getTimeDate());
  }

  private ScheduleCommand scheduleCycle(
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      return scheduleCron(processDefinition, startEvent, timerEventDefinition);
    } else {
      return scheduleFixedRate(processDefinition, startEvent, timerEventDefinition);
    }
  }

  private ScheduleCommand scheduleFixedRate(
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<SchedulableMessage> triggers = getStartCommands(processDefinition, startEvent);

    RepeatDuration repeatDuration = RepeatDuration.parse(timerEventDefinition.getTimeCycle());
    return new FixedRateStartCommand(
        triggers,
        repeatDuration.getDuration(),
        repeatDuration.getRepetitions(),
        0,
        Instant.now(clock).toString());
  }

  private ScheduleCommand scheduleCron(
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<SchedulableMessage> messages = getStartCommands(processDefinition, startEvent);
    return new RecurringStartCommand(
        messages, timerEventDefinition.getTimeCycle(), Instant.now(clock).toString());
  }

  private static List<SchedulableMessage> getStartCommands(
      ProcessDefinition processDefinition, StartEvent startEvent) {
    List<SchedulableMessage> processInstanceStartCommand = new ArrayList<>();
    for (String outgoingFlowId : startEvent.getOutgoing()) {
      SequenceFlow sequenceFlow =
          (SequenceFlow)
              processDefinition
                  .getDefinitions()
                  .getRootProcess()
                  .getFlowElements()
                  .getFlowElement(outgoingFlowId)
                  .orElseThrow();
      processInstanceStartCommand.add(
          new StartCommand(
              ProcessInstanceKey.NONE,
              Constants.NONE,
              processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
              Variables.EMPTY));
    }
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
