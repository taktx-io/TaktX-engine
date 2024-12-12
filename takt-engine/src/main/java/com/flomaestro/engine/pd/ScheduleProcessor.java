package com.flomaestro.engine.pd;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.takt.dto.v_1_0_0.MessageSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.SchedulableMessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduledKeyDTO;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ScheduleProcessor
    implements Processor<ScheduledKeyDTO, MessageSchedulerDTO, Object, SchedulableMessageDTO> {

  public static final int SCHEDULE_INTERVAL = 100;
  private ProcessorContext<Object, SchedulableMessageDTO> context;
  private final Clock clock;
  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;

  public ScheduleProcessor(Clock clock, TenantNamespaceNameWrapper tenantNamespaceNameWrapper) {
    this.clock = clock;
    this.tenantNamespaceNameWrapper = tenantNamespaceNameWrapper;
  }

  @Override
  public void init(ProcessorContext<Object, SchedulableMessageDTO> context) {
    this.context = context;
    context.schedule(
        Duration.ofMillis(SCHEDULE_INTERVAL),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp -> {
          long startTime = System.currentTimeMillis();
          KeyValueStore<ScheduledKeyDTO, MessageSchedulerDTO> scheduleStore =
              context.getStateStore(
                  tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES.getStorename()));
          try (KeyValueIterator<ScheduledKeyDTO, MessageSchedulerDTO> all = scheduleStore.all()) {
            all.forEachRemaining(
                scheduledKeyValue -> {
                  ScheduledKeyDTO scheduledKey = scheduledKeyValue.key;
                  MessageSchedulerDTO scheduleCommand = scheduledKeyValue.value;
                  if (scheduleCommand != null) {
                    MessageSchedulerDTO updatedScheduleCommand =
                        scheduleCommand.evaluate(
                            Instant.now(clock),
                            (processInstanceKey, schedulableMessages) ->
                                schedulableMessages.forEach(
                                    message ->
                                        context.forward(
                                            new Record<>(
                                                message.getRecordKey(processInstanceKey),
                                                message,
                                                Instant.now(clock).toEpochMilli()))));
                    if (updatedScheduleCommand != null
                        && !updatedScheduleCommand.equals(scheduleCommand)) {
                      scheduleStore.put(scheduledKey, updatedScheduleCommand);
                    } else if (updatedScheduleCommand == null) {
                      scheduleStore.delete(scheduledKey);
                    }
                  }
                });
          }
          long endTime = System.currentTimeMillis();
          long processingTime = endTime - startTime;
          if (processingTime > SCHEDULE_INTERVAL) {
            log.warn(
                "Backlog detected: processing time {} ms exceeds scheduled interval",
                processingTime);
          }
        });
  }

  @Override
  public void process(Record<ScheduledKeyDTO, MessageSchedulerDTO> scheduleRecord) {
    KeyValueStore<ScheduledKeyDTO, MessageSchedulerDTO> scheduleStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES.getStorename()));
    if (scheduleRecord.value() != null) {
      scheduleStore.put(scheduleRecord.key(), scheduleRecord.value());
    } else {
      scheduleStore.delete(scheduleRecord.key());
    }
  }
}
