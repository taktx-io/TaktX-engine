package nl.qunit.bpmnmeister.engine.pd;

import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.Message;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pd.model.StartEvent;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import nl.qunit.bpmnmeister.scheduler.ScheduleType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class ProcessDefinitionActivationProcessor
    implements Processor<ProcessDefinitionKey, ProcessDefinitionActivation, Object, Object> {

  private ProcessorContext<Object, Object> context;
  private final MessageSchedulerFactory messageSchedulerFactory;

  public ProcessDefinitionActivationProcessor(MessageSchedulerFactory messageSchedulerFactory) {
    this.messageSchedulerFactory = messageSchedulerFactory;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
  }

  private static List<SchedulableMessage<?>> getStartCommands(
      ProcessDefinition processDefinition, StartEvent startEvent) {
    List<SchedulableMessage<?>> processInstanceStartCommand = new ArrayList<>();
    processInstanceStartCommand.add(
        new StartCommand(
            ProcessInstanceKey.NONE,
            startEvent.getParentId(),
            Constants.NONE,
            processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
            Variables.EMPTY));

    return processInstanceStartCommand;
  }

  @Override
  public void process(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord) {
    if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.ACTIVE) {
      ProcessDefinitionActivation processActivation = processActivationRecord.value();
      ProcessDefinition processDefinition = processActivation.getProcessDefinition();
      processDefinition
          .getDefinitions()
          .getRootProcess()
          .getFlowElements()
          .getStartEvents()
          .forEach(
              startEvent -> {
                scheduleStartCommands(processActivationRecord, startEvent, processDefinition);
                subscribetoStartMessageEvents(
                    processActivationRecord, startEvent, processDefinition);
              });
    } else if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.INACTIVE) {
      ProcessDefinitionActivation processActivation = processActivationRecord.value();
      ProcessDefinition processDefinition = processActivation.getProcessDefinition();
      processDefinition
          .getDefinitions()
          .getRootProcess()
          .getFlowElements()
          .getStartEvents()
          .forEach(
              startEvent -> {
                cancelScheduledStartCommands(processActivationRecord, startEvent);
                unsubscribeFromStartMessageEvents(
                    processActivationRecord, startEvent, processDefinition);
              });
    }
  }

  private void subscribetoStartMessageEvents(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord,
      StartEvent startEvent,
      ProcessDefinition processDefinition) {
    startEvent
        .getMessageStartEventDefinitions()
        .forEach(
            messageStartEventDefinition -> {
              String messageRef = messageStartEventDefinition.getMessageRef();
              Message message = processDefinition.getDefinitions().getMessages().get(messageRef);
              String name = message.getName();
              MessageEventKey key = new MessageEventKey(name);
              MessageSubscription messageSubscription =
                  new MessageSubscription(processActivationRecord.key(), startEvent.getId(), name);

              context.forward(
                  new Record<>(key, messageSubscription, processActivationRecord.timestamp()));
            });
  }

  private void unsubscribeFromStartMessageEvents(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord,
      StartEvent startEvent,
      ProcessDefinition processDefinition) {
    startEvent
        .getMessageStartEventDefinitions()
        .forEach(
            messageStartEventDefinition -> {
              String messageRef = messageStartEventDefinition.getMessageRef();
              Message message = processDefinition.getDefinitions().getMessages().get(messageRef);
              String name = message.getName();
              MessageEventKey key = new MessageEventKey(name);
              context.forward(new Record<>(key, null, processActivationRecord.timestamp()));
            });
  }

  private void cancelScheduledStartCommands(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord,
      StartEvent startEvent) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              ScheduleKey scheduleKey =
                  new ScheduleKey(
                      processActivationRecord.key(),
                      ProcessInstanceKey.NONE,
                      ScheduleType.from(timerEventDefinition),
                      startEvent.getId(),
                      timerEventDefinition.getId());
              context.forward(new Record<>(scheduleKey, null, processActivationRecord.timestamp()));
            });
  }

  private void scheduleStartCommands(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord,
      StartEvent startEvent,
      ProcessDefinition processDefinition) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              MessageScheduler schedule =
                  messageSchedulerFactory.schedule(
                      processActivationRecord.key(),
                      ProcessInstanceKey.NONE,
                      startEvent.getId(),
                      timerEventDefinition,
                      getStartCommands(processDefinition, startEvent),
                      Variables.EMPTY);
              context.forward(
                  new Record<>(
                      schedule.getScheduleKey(), schedule, processActivationRecord.timestamp()));
            });
  }
}
