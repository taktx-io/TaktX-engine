package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.engine.RepeatDuration;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;
import nl.qunit.bpmnmeister.scheduler.model.command.FixedRateCommand;
import nl.qunit.bpmnmeister.scheduler.model.command.OneTimeCommand;
import nl.qunit.bpmnmeister.scheduler.model.command.RecurringCommand;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class TimerDefinitionScheduler {
  final Emitter<RecurringCommand> recurringCommandEmitter;
  final Emitter<FixedRateCommand> fixedRateCommandEmitter;
  final Emitter<OneTimeCommand> oneTimeCommandEmitter;

  public TimerDefinitionScheduler(
      @Channel("recurring-outgoing") Emitter<RecurringCommand> recurringCommandEmitter,
      @Channel("fixed-rate-outgoing") Emitter<FixedRateCommand> fixedRateCommandEmitter,
      @Channel("one-time-outgoing") Emitter<OneTimeCommand> oneTimeCommandEmitter) {
    this.recurringCommandEmitter = recurringCommandEmitter;
    this.fixedRateCommandEmitter = fixedRateCommandEmitter;
    this.oneTimeCommandEmitter = oneTimeCommandEmitter;
  }

  public void schedule(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    if (timerEventDefinition.getTimeCycle() != null) {
      scheduleCycle(processDefinition, startEvent, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDate() != null) {
      scheduleOneTime(processDefinition, startEvent, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDuration() != null) {
      scheduleDuration(timerEventDefinition);
    }
  }

  public void cancel(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    if (timerEventDefinition.getTimeCycle() != null) {
      cancelCycle(processDefinition, startEvent, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDate() != null) {
      cancelOneTime(processDefinition, startEvent, timerEventDefinition);
    } else if (timerEventDefinition.getTimeDuration() != null) {
      cancelDuration(timerEventDefinition);
    }
  }

  private void cancelDuration(TimerEventDefinition timerEventDefinition) {}

  private void cancelOneTime(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    String timerId = getTimerId(processDefinition, startEvent, timerEventDefinition);
    oneTimeCommandEmitter.send(KafkaRecord.of(timerId, null));
  }

  private void cancelCycle(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      cancelCron(processDefinition, startEvent, timerEventDefinition);
    } else {
      cancelFixedRate(processDefinition, startEvent, timerEventDefinition);
    }
  }

  private void cancelFixedRate(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    String timerId = getTimerId(processDefinition, startEvent, timerEventDefinition);
    fixedRateCommandEmitter.send(KafkaRecord.of(timerId, null));
  }

  private void cancelCron(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    String timerId = getTimerId(processDefinition, startEvent, timerEventDefinition);
    recurringCommandEmitter.send(KafkaRecord.of(timerId, null));
  }

  private void scheduleDuration(TimerEventDefinition timerEventDefinition) {}

  private void scheduleOneTime(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<ProcessInstanceTrigger> triggers =
        getTriggers(processDefinition, startEvent, timerEventDefinition);

    OneTimeCommand fixedRateCommand =
        new OneTimeCommand(
            getTimerId(processDefinition, startEvent, timerEventDefinition),
            triggers,
            timerEventDefinition.getTimeDate());
    oneTimeCommandEmitter.send(KafkaRecord.of(fixedRateCommand.id(), fixedRateCommand));
  }

  private String getTimerId(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    return processDefinition.getProcessDefinitionId()
        + "-"
        + processDefinition.getVersion()
        + '-'
        + startEvent.getId()
        + '-'
        + timerEventDefinition.getId();
  }

  private void scheduleCycle(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      scheduleCron(processDefinition, startEvent, timerEventDefinition);
    } else {
      scheduleFixedRate(processDefinition, startEvent, timerEventDefinition);
    }
  }

  private void scheduleFixedRate(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<ProcessInstanceTrigger> triggers =
        getTriggers(processDefinition, startEvent, timerEventDefinition);
    RepeatDuration repeatDuration = RepeatDuration.parse(timerEventDefinition.getTimeCycle());
    FixedRateCommand fixedRateCommand =
        new FixedRateCommand(
            getTimerId(processDefinition, startEvent, timerEventDefinition),
            triggers,
            repeatDuration.getDuration(),
            repeatDuration.getRepetitions(),
            0);
    fixedRateCommandEmitter.send(KafkaRecord.of(fixedRateCommand.id(), fixedRateCommand));
  }

  private void scheduleCron(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<ProcessInstanceTrigger> triggers =
        getTriggers(processDefinition, startEvent, timerEventDefinition);
    RecurringCommand recurringCommand =
        new RecurringCommand(
            getTimerId(processDefinition, startEvent, timerEventDefinition),
            triggers,
            timerEventDefinition.getTimeCycle());
    recurringCommandEmitter.send(KafkaRecord.of(recurringCommand.id(), recurringCommand));
  }

  private static List<ProcessInstanceTrigger> getTriggers(
      Definitions processDefinition,
      StartEvent startEvent,
      TimerEventDefinition timerEventDefinition) {
    List<ProcessInstanceTrigger> triggers = new ArrayList<>();
    for (String outgoingFlowId : startEvent.getOutgoing()) {
      SequenceFlow sequenceFlow =
          (SequenceFlow) processDefinition.getFlowElement(outgoingFlowId).orElseThrow();
      triggers.add(
          new ProcessInstanceTrigger(
              null,
              processDefinition.getProcessDefinitionId(),
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
