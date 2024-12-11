package nl.qunit.bpmnmeister.engine.pd;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.engine.generic.TenantNamespaceNameWrapper;
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
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ProcessDefinitionActivationProcessor {
  private final MessageSchedulerFactory messageSchedulerFactory;
  private final ProcessorContext<Object, Object> context;
  private final KeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> processDefinitionStore;

  public ProcessDefinitionActivationProcessor(
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper,
      MessageSchedulerFactory messageSchedulerFactory,
      ProcessorContext<Object, Object> context) {
    this.messageSchedulerFactory = messageSchedulerFactory;
    this.context = context;
    this.processDefinitionStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.PROCESS_DEFINITION.getStorename()));
  }

  public void process(ProcessDefinitionActivationDTO processActivationRecord) {
    if (processActivationRecord.getState() == ProcessDefinitionStateEnum.ACTIVE) {
      activate(processActivationRecord.getProcessDefinitionKey());
    } else if (processActivationRecord.getState() == ProcessDefinitionStateEnum.INACTIVE) {
      deactivate(processActivationRecord.getProcessDefinitionKey());
    }
  }

  public void activate(ProcessDefinitionKey processDefinitionKey) {
    // Deactivate all other versions of the process definition
    ProcessDefinitionKey startKey =
        new ProcessDefinitionKey(processDefinitionKey.getProcessDefinitionId(), 1);
    ProcessDefinitionKey endKey =
        new ProcessDefinitionKey(processDefinitionKey.getProcessDefinitionId(), Integer.MAX_VALUE);

    processDefinitionStore
        .range(startKey, endKey)
        .forEachRemaining(
            entry -> {
              if (!entry.key.equals(processDefinitionKey)) {
                deactivate(entry.key);
              }
            });

    ProcessDefinitionDTO processDefinition = processDefinitionStore.get(processDefinitionKey);

    ProcessDefinitionDTO activatedDefinition =
        new ProcessDefinitionDTO(
            processDefinition.getDefinitions(),
            processDefinition.getVersion(),
            ProcessDefinitionStateEnum.ACTIVE);
    processDefinitionStore.put(processDefinitionKey, activatedDefinition);

    processDefinition
        .getDefinitions()
        .getRootProcess()
        .getFlowElements()
        .getStartEvents()
        .forEach(
            startEvent -> {
              scheduleStartCommands(processDefinitionKey, startEvent);
              subscribetoStartMessageEvents(processDefinitionKey, startEvent, processDefinition);
            });

    context.forward(
        new Record<>(processDefinitionKey, activatedDefinition, Instant.now().toEpochMilli()));
  }

  public void deactivate(ProcessDefinitionKey processDefinitionKey) {
    ProcessDefinitionDTO previousActiveProcessDefinition =
        processDefinitionStore.get(processDefinitionKey);
    if (previousActiveProcessDefinition.getState() == ProcessDefinitionStateEnum.INACTIVE) {
      return;
    }

    ProcessDefinitionDTO deactivatedProcessDefinition =
        new ProcessDefinitionDTO(
            previousActiveProcessDefinition.getDefinitions(),
            processDefinitionKey.getVersion(),
            ProcessDefinitionStateEnum.INACTIVE);
    processDefinitionStore.put(processDefinitionKey, deactivatedProcessDefinition);

    deactivatedProcessDefinition
        .getDefinitions()
        .getRootProcess()
        .getFlowElements()
        .getStartEvents()
        .forEach(
            startEvent -> {
              cancelScheduledStartCommands(processDefinitionKey, startEvent);
              unsubscribeFromStartMessageEvents(startEvent, deactivatedProcessDefinition);
            });

    context.forward(
        new Record<>(
            processDefinitionKey, deactivatedProcessDefinition, Instant.now().toEpochMilli()));
  }

  private void subscribetoStartMessageEvents(
      ProcessDefinitionKey processDefinitionKey,
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
                      processDefinitionKey, startEvent.getId(), messageName);

              context.forward(
                  new Record<>(
                      messageSubscription.toMessageEventKey(),
                      messageSubscription,
                      Instant.now().toEpochMilli()));
            });
  }

  private void unsubscribeFromStartMessageEvents(
      StartEventDTO startEvent, ProcessDefinitionDTO processDefinition) {
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
                      Instant.now().toEpochMilli()));
            });
  }

  private void cancelScheduledStartCommands(
      ProcessDefinitionKey processDefinitionKey, StartEventDTO startEvent) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              ScheduledKeyDTO scheduledKey =
                  new ScheduledKeyDTO(
                      processDefinitionKey,
                      Constants.NONE_UUID,
                      ScheduleType.from(timerEventDefinition),
                      startEvent.getId(),
                      timerEventDefinition.getId());
              context.forward(new Record<>(scheduledKey, null, Instant.now().toEpochMilli()));
            });
  }

  private void scheduleStartCommands(
      ProcessDefinitionKey processDefinitionKey, StartEventDTO startEvent) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              MessageSchedulerDTO schedule =
                  messageSchedulerFactory.schedule(
                      processDefinitionKey,
                      Constants.NONE_UUID,
                      startEvent.getId(),
                      timerEventDefinition,
                      getStartCommands(processDefinitionKey.getProcessDefinitionId(), startEvent),
                      Variables.empty());
              context.forward(
                  new Record<>(schedule.getScheduledKey(), schedule, Instant.now().toEpochMilli()));
            });
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
}
