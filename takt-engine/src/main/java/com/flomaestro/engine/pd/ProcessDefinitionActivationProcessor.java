package com.flomaestro.engine.pd;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.CancelDefinitionMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.Constants;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceScheduleKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionActivationDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.SchedulableMessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    ProcessDefinitionKey latestKey =
        new ProcessDefinitionKey(processDefinitionKey.getProcessDefinitionId(), -1);
    context.forward(new Record<>(latestKey, activatedDefinition, Instant.now().toEpochMilli()));
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
              InstanceScheduleKeyDTO scheduleKey =
                  new InstanceScheduleKeyDTO(UUID.randomUUID(), Constants.NONE_UUID);
              context.forward(new Record<>(scheduleKey, null, Instant.now().toEpochMilli()));
            });
  }

  private void scheduleStartCommands(
      ProcessDefinitionKey processDefinitionKey, StartEventDTO startEvent) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              UUID processInstanceKey = UUID.randomUUID();
              InstanceScheduleKeyDTO scheduleKey =
                  new InstanceScheduleKeyDTO(processInstanceKey, Constants.NONE_UUID);
              MessageSchedulerDTO schedule =
                  messageSchedulerFactory.schedule(
                      scheduleKey,
                      timerEventDefinition,
                      getStartCommand(
                          processDefinitionKey.getProcessDefinitionId(),
                          processInstanceKey,
                          startEvent),
                      Variables.empty());
              context.forward(new Record<>(scheduleKey, schedule, Instant.now().toEpochMilli()));
            });
  }

  private static SchedulableMessageDTO getStartCommand(
      String processDefinitionId, UUID processInstanceKey, StartEventDTO startEvent) {
    return new StartCommandDTO(
        processInstanceKey,
        Constants.NONE_UUID,
        startEvent.getParentId(),
        List.of(),
        List.of(),
        new ProcessDefinitionKey(processDefinitionId),
        VariablesDTO.empty());
  }
}
