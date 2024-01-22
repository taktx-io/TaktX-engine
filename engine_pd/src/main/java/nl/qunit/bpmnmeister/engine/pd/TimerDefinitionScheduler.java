package nl.qunit.bpmnmeister.engine.pd;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.pd.model.*;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.scheduler.*;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;

@ApplicationScoped
public class TimerDefinitionScheduler {

  public ScheduleCommand schedule(
      ScheduleKey scheduleKey,
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    if (timerEventDefinition.getTimeCycle() != null) {
      return scheduleCycle(scheduleKey, processDefinition, startEvent, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDate() != null) {
      return scheduleOneTime(scheduleKey, processDefinition, startEvent, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDuration() != null) {
      return scheduleDuration(scheduleKey, timerEventDefinition);
    }
    throw new IllegalArgumentException("TimerEventDefinition is not valid");
  }

  private ScheduleCommand scheduleDuration(
      ScheduleKey scheduleKey, TimerEventDefinition timerEventDefinition) {
    return null;
  }

  private ScheduleCommand scheduleOneTime(
      ScheduleKey scheduleKey,
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<ProcessInstanceTrigger> triggers = getTriggers(processDefinition, startEvent);

    return new OneTimeCommand(scheduleKey, triggers, timerEventDefinition.getTimeDate());
  }

  private ScheduleCommand scheduleCycle(
      ScheduleKey scheduleKey,
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      return scheduleCron(scheduleKey, processDefinition, startEvent, timerEventDefinition);
    } else {
      return scheduleFixedRate(scheduleKey, processDefinition, startEvent, timerEventDefinition);
    }
  }

  private ScheduleCommand scheduleFixedRate(
      ScheduleKey scheduleKey,
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<ProcessInstanceTrigger> triggers = getTriggers(processDefinition, startEvent);
    RepeatDuration repeatDuration = RepeatDuration.parse(timerEventDefinition.getTimeCycle());
    return new FixedRateCommand(
        scheduleKey, triggers, repeatDuration.getDuration(), repeatDuration.getRepetitions(), 0);
  }

  private ScheduleCommand scheduleCron(
      ScheduleKey scheduleKey,
      ProcessDefinition processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<ProcessInstanceTrigger> triggers = getTriggers(processDefinition, startEvent);
    return new RecurringCommand(scheduleKey, triggers, timerEventDefinition.getTimeCycle());
  }

  private static List<ProcessInstanceTrigger> getTriggers(
      ProcessDefinition processDefinition, StartEvent startEvent) {
    List<ProcessInstanceTrigger> triggers = new ArrayList<>();
    for (String outgoingFlowId : startEvent.getOutgoing()) {
      SequenceFlow sequenceFlow =
          (SequenceFlow) processDefinition.getFlowElement(outgoingFlowId).orElseThrow();
      triggers.add(
          new ProcessInstanceTrigger(
              null,
              processDefinition.getDefinitions().getProcessDefinitionId(),
              processDefinition.getVersion(),
              sequenceFlow.getTarget(),
              sequenceFlow.getId()));
    }
    return triggers;
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
