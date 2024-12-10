package nl.qunit.bpmnmeister.engine.pd;

import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.engine.pi.model.Variables;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.Constants;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.MessageDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.StartEventDTO;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.MessageEventDTO;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.CancelDefinitionMessageSubscriptionDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.DefinitionMessageSubscriptionDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ProcessDefinitionActivationDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.StartCommandDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.MessageSchedulerDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.SchedulableMessageDTO;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.ScheduleType;
import nl.qunit.bpmnmeister.scheduler.v_1_0_0.ScheduledKeyDTO;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class ProcessDefinitionActivationProcessor
    implements Processor<ProcessDefinitionKey, ProcessDefinitionActivationDTO, Object, Object> {

  private ProcessorContext<Object, Object> context;
  private final MessageSchedulerFactory messageSchedulerFactory;

  public ProcessDefinitionActivationProcessor(MessageSchedulerFactory messageSchedulerFactory) {
    this.messageSchedulerFactory = messageSchedulerFactory;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
  }

  private static List<SchedulableMessageDTO<?>> getStartCommands(
      String processDefinitionId, StartEventDTO startEvent) {
    List<SchedulableMessageDTO<?>> processInstanceStartCommand = new ArrayList<>();
    processInstanceStartCommand.add(
        new StartCommandDTO(
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
      Record<ProcessDefinitionKey, ProcessDefinitionActivationDTO> processActivationRecord) {
    if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.ACTIVE) {
      ProcessDefinitionActivationDTO processActivation = processActivationRecord.value();
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
      ProcessDefinitionActivationDTO processActivation = processActivationRecord.value();
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
      Record<ProcessDefinitionKey, ProcessDefinitionActivationDTO> processActivationRecord,
      StartEventDTO startEvent,
      ProcessDefinitionDTO processDefinition) {
    startEvent
        .getMessageventDefinitions()
        .forEach(
            messageStartEventDefinition -> {
              String messageRef = messageStartEventDefinition.getMessageRef();
              MessageDTO message = processDefinition.getDefinitions().getMessages().get(messageRef);
              String messageName = message.getName();
              MessageEventDTO messageSubscription =
                  new DefinitionMessageSubscriptionDTO(
                      processActivationRecord.key(), startEvent.getId(), messageName);

              context.forward(
                  new Record<>(
                      messageSubscription.toMessageEventKey(),
                      messageSubscription,
                      processActivationRecord.timestamp()));
            });
  }

  private void unsubscribeFromStartMessageEvents(
      Record<ProcessDefinitionKey, ProcessDefinitionActivationDTO> processActivationRecord,
      StartEventDTO startEvent,
      ProcessDefinitionDTO processDefinition) {
    startEvent
        .getMessageventDefinitions()
        .forEach(
            messageStartEventDefinition -> {
              String messageRef = messageStartEventDefinition.getMessageRef();
              MessageDTO message = processDefinition.getDefinitions().getMessages().get(messageRef);
              String messageName = message.getName();
              MessageEventDTO cancelSubscription =
                  new CancelDefinitionMessageSubscriptionDTO(messageName);
              context.forward(
                  new Record<>(
                      cancelSubscription.toMessageEventKey(),
                      cancelSubscription,
                      processActivationRecord.timestamp()));
            });
  }

  private void cancelScheduledStartCommands(
      Record<ProcessDefinitionKey, ProcessDefinitionActivationDTO> processActivationRecord,
      StartEventDTO startEvent) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              ScheduledKeyDTO scheduledKey =
                  new ScheduledKeyDTO(
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
      Record<ProcessDefinitionKey, ProcessDefinitionActivationDTO> processActivationRecord,
      StartEventDTO startEvent,
      String processDefinitionId) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              MessageSchedulerDTO schedule =
                  messageSchedulerFactory.schedule(
                      processActivationRecord.key(),
                      Constants.NONE_UUID,
                      startEvent.getId(),
                      timerEventDefinition,
                      getStartCommands(processDefinitionId, startEvent),
                      Variables.empty());
              context.forward(
                  new Record<>(
                      schedule.getScheduledKey(), schedule, processActivationRecord.timestamp()));
            });
  }
}
