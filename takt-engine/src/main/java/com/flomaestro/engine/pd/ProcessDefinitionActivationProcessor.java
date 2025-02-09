package com.flomaestro.engine.pd;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.engine.pi.model.VariableScope;
import com.flomaestro.takt.dto.v_1_0_0.CancelDefinitionMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionMessageSubscriptionDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceScheduleKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionActivationDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionKey;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.SchedulableMessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartCommandDTO;
import com.flomaestro.takt.dto.v_1_0_0.StartEventDTO;
import com.flomaestro.takt.dto.v_1_0_0.TimeBucket;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ProcessDefinitionActivationProcessor {
  private final MessageSchedulerFactory messageSchedulerFactory;
  private final ProcessorContext<Object, Object> context;
  private final Clock clock;
  private final KeyValueStore<ProcessDefinitionKey, ProcessDefinitionDTO> processDefinitionStore;

  public ProcessDefinitionActivationProcessor(
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper,
      MessageSchedulerFactory messageSchedulerFactory,
      ProcessorContext<Object, Object> context,
      Clock clock) {
    this.messageSchedulerFactory = messageSchedulerFactory;
    this.context = context;
    this.clock = clock;
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

    context.forward(new Record<>(processDefinitionKey, activatedDefinition, clock.millis()));
    ProcessDefinitionKey latestKey =
        new ProcessDefinitionKey(processDefinitionKey.getProcessDefinitionId(), -1);
    context.forward(new Record<>(latestKey, activatedDefinition, clock.millis()));
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
        new Record<>(processDefinitionKey, deactivatedProcessDefinition, clock.millis()));
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
                      clock.millis()));
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
                      cancelSubscription.toMessageEventKey(), cancelSubscription, clock.millis()));
            });
  }

  private void cancelScheduledStartCommands(
      ProcessDefinitionKey processDefinitionKey, StartEventDTO startEvent) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              //              DefinitionScheduleKeyDTO scheduleKey =
              //                  new DefinitionScheduleKeyDTO(processDefinitionKey,
              // startEvent.getId());
              //              context.forward(new Record<>(scheduleKey, null, clock.millis()));
            });
  }

  private void scheduleStartCommands(
      ProcessDefinitionKey processDefinitionKey, StartEventDTO startEvent) {
    startEvent
        .getTimerEventDefinitions()
        .forEach(
            timerEventDefinition -> {
              UUID processInstanceKey = UUID.randomUUID();
              MessageScheduleDTO schedule =
                  messageSchedulerFactory.schedule(
                      timerEventDefinition,
                      getStartCommand(
                          processDefinitionKey.getProcessDefinitionId(),
                          processInstanceKey,
                          startEvent),
                      new VariableScope(null, null, null, null));
              TimeBucket timeBucket = schedule.getTimeBucket(clock.millis());
              if (timeBucket != null) {
                InstanceScheduleKeyDTO scheduleKey =
                    new InstanceScheduleKeyDTO(processInstanceKey, List.of(), timeBucket);
                context.forward(new Record<>(scheduleKey, schedule, clock.millis()));
              }
            });
  }

  private static SchedulableMessageDTO getStartCommand(
      String processDefinitionId, UUID processInstanceKey, StartEventDTO startEvent) {
    return new StartCommandDTO(
        processInstanceKey,
        startEvent.getParentId() != null ? List.of(startEvent.getParentId()) : null,
        null,
        new ProcessDefinitionKey(processDefinitionId),
        VariablesDTO.empty());
  }
}
