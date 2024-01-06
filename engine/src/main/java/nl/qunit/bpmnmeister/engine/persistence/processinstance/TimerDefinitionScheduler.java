package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.RepeatDuration;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;
import nl.qunit.bpmnmeister.model.scheduler.FixedRateCommand;
import nl.qunit.bpmnmeister.model.scheduler.OneTimeCommand;
import nl.qunit.bpmnmeister.model.scheduler.RecurringCommand;
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
      scheduleOneTime(timerEventDefinition);
    } else if (timerEventDefinition.getTimeDuration() != null) {
      scheduleDuration(timerEventDefinition);
    }
  }

  private void scheduleDuration(TimerEventDefinition timerEventDefinition) {}

  private void scheduleOneTime(TimerEventDefinition timerEventDefinition) {}

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
    List<Trigger> triggers = getTriggers(processDefinition, startEvent);
    RepeatDuration repeatDuration = RepeatDuration.parse(timerEventDefinition.getTimeCycle());
    FixedRateCommand fixedRateCommand =
        new FixedRateCommand(
            UUID.randomUUID(),
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
    List<Trigger> triggers = getTriggers(processDefinition, startEvent);
    RecurringCommand recurringCommand =
        new RecurringCommand(UUID.randomUUID(), triggers, timerEventDefinition.getTimeCycle());
    recurringCommandEmitter.send(KafkaRecord.of(recurringCommand.id(), recurringCommand));
  }

  private static List<Trigger> getTriggers(Definitions processDefinition, StartEvent startEvent) {
    List<Trigger> triggers = new ArrayList<>();
    for (String outgoingFlowId : startEvent.getOutgoing()) {
      SequenceFlow sequenceFlow =
          (SequenceFlow) processDefinition.getFlowElement(outgoingFlowId).orElseThrow();
      triggers.add(
          new Trigger(
              null,
              processDefinition.getProcessDefinitionId(),
              processDefinition.getVersion(),
              sequenceFlow.getTarget(),
              sequenceFlow.getId(),
              null));
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
