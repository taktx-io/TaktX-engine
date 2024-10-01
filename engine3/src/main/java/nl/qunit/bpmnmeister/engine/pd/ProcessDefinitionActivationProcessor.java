package nl.qunit.bpmnmeister.engine.pd;

import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.MessageDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pd.model.StartEventDTO;
import nl.qunit.bpmnmeister.pi.CancelDefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.DefinitionMessageSubscription;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import nl.qunit.bpmnmeister.pi.state.MessageEvent;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduleType;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;
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
      String processDefinitionId, StartEventDTO startEvent) {
    List<SchedulableMessage<?>> processInstanceStartCommand = new ArrayList<>();
    processInstanceStartCommand.add(
        new StartCommand(
            Constants.NONE_UUID,
            Constants.NONE_UUID,
            startEvent.getParentId(),
            List.of(),
            List.of(),
            processDefinitionId,
            VariablesDTO.empty()));

    return processInstanceStartCommand;
  }

  @Override
  public void process(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord) {
    if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.ACTIVE) {
      ProcessDefinitionActivation processActivation = processActivationRecord.value();
      ProcessDefinitionDTO processDefinition = processActivation.getProcessDefinition();

      processDefinition
          .getDefinitions()
          .getRootProcess()
          .getFlowElements()
          .getStartEvents()
          .forEach(
              startEvent -> {
                scheduleStartCommands(
                    processActivationRecord,
                    startEvent,
                    processDefinition
                        .getDefinitions()
                        .getDefinitionsKey()
                        .getProcessDefinitionId());
                subscribetoStartMessageEvents(
                    processActivationRecord, startEvent, processDefinition);
              });
    } else if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.INACTIVE) {
      ProcessDefinitionActivation processActivation = processActivationRecord.value();
      ProcessDefinitionDTO processDefinition = processActivation.getProcessDefinition();
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
      StartEventDTO startEvent,
      ProcessDefinitionDTO processDefinition) {
    startEvent
        .getMessageventDefinitions()
        .forEach(
            messageStartEventDefinition -> {
              String messageRef = messageStartEventDefinition.getMessageRef();
              MessageDTO message = processDefinition.getDefinitions().getMessages().get(messageRef);
              String messageName = message.getName();
              MessageEvent messageSubscription =
                  new DefinitionMessageSubscription(
                      processActivationRecord.key(), startEvent.getId(), messageName);

              context.forward(
                  new Record<>(
                      messageSubscription.toMessageEventKey(),
                      messageSubscription,
                      processActivationRecord.timestamp()));
            });
  }

  private void unsubscribeFromStartMessageEvents(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord,
      StartEventDTO startEvent,
      ProcessDefinitionDTO processDefinition) {
    startEvent
        .getMessageventDefinitions()
        .forEach(
            messageStartEventDefinition -> {
              String messageRef = messageStartEventDefinition.getMessageRef();
              MessageDTO message = processDefinition.getDefinitions().getMessages().get(messageRef);
              String messageName = message.getName();
              MessageEvent cancelSubscription =
                  new CancelDefinitionMessageSubscription(messageName);
              context.forward(
                  new Record<>(
                      cancelSubscription.toMessageEventKey(),
                      cancelSubscription,
                      processActivationRecord.timestamp()));
            });
  }

  private void cancelScheduledStartCommands(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord,
      StartEventDTO startEvent) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              ScheduledKey scheduledKey =
                  new ScheduledKey(
                      processActivationRecord.key(),
                      Constants.NONE_UUID,
                      ScheduleType.from(timerEventDefinition),
                      startEvent.getId(),
                      timerEventDefinition.getId());
              context.forward(
                  new Record<>(scheduledKey, null, processActivationRecord.timestamp()));
            });
  }

  private void scheduleStartCommands(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord,
      StartEventDTO startEvent,
      String processDefinitionId) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              MessageScheduler schedule =
                  messageSchedulerFactory.schedule(
                      processActivationRecord.key(),
                      Constants.NONE_UUID,
                      startEvent.getId(),
                      timerEventDefinition,
                      getStartCommands(processDefinitionId, startEvent),
                      Variables2.empty());
              context.forward(
                  new Record<>(
                      schedule.getScheduledKey(), schedule, processActivationRecord.timestamp()));
            });
  }
}
