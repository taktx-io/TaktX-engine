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
import nl.qunit.bpmnmeister.pd.model.*;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.scheduler.*;

@ApplicationScoped
public class ScheduleCommandFactory {
  @Inject Clock clock;

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
    List<ProcessInstanceTrigger> triggers = getTriggers(processDefinition, startEvent);

    return new OneTimeCommand(triggers, timerEventDefinition.getTimeDate());
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
    List<ProcessInstanceTrigger> triggers = getTriggers(processDefinition, startEvent);
    RepeatDuration repeatDuration = RepeatDuration.parse(timerEventDefinition.getTimeCycle());
    return new FixedRateCommand(
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
    List<ProcessInstanceTrigger> triggers = getTriggers(processDefinition, startEvent);
    return new RecurringCommand(
        triggers, timerEventDefinition.getTimeCycle(), Instant.now(clock).toString());
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
              processDefinition.getDefinitions().getGeneration(),
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
