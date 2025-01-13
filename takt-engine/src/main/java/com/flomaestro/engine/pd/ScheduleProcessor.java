package com.flomaestro.engine.pd;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.takt.dto.v_1_0_0.MessageSchedulerDTO;
import com.flomaestro.takt.dto.v_1_0_0.SchedulableMessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ScheduleProcessor
    implements Processor<ScheduleKeyDTO, MessageSchedulerDTO, Object, SchedulableMessageDTO> {

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
          AtomicInteger nrSchedulesProcessed = new AtomicInteger(0);
          long startTime = System.currentTimeMillis();
          KeyValueStore<ScheduleKeyDTO, MessageSchedulerDTO> scheduleStore =
              context.getStateStore(
                  tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES.getStorename()));
          try (KeyValueIterator<ScheduleKeyDTO, MessageSchedulerDTO> all = scheduleStore.all()) {
            all.forEachRemaining(
                scheduledKeyValue -> {
                  nrSchedulesProcessed.incrementAndGet();
                  ScheduleKeyDTO scheduledKey = scheduledKeyValue.key;
                  MessageSchedulerDTO scheduleCommand = scheduledKeyValue.value;
                  if (scheduleCommand != null) {
                    Instant now = Instant.now(clock);
                    MessageSchedulerDTO updatedScheduleCommand =
                        scheduleCommand.evaluate(
                            now,
                            (scheduleKey, message) -> {
                              log.info(
                                  "Sending scheduled message at {} {}, {}",
                                  now,
                                  scheduleKey,
                                  message);
                              context.forward(
                                  new Record<>(
                                      scheduledKey.getRecordKey(), message, clock.millis()));
                            });
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
            log.error(
                "Backlog detected: processing time {} ms exceeds scheduled interval for {} schedules",
                processingTime,
                nrSchedulesProcessed.get());
          }
        });
  }

  @Override
  public void process(Record<ScheduleKeyDTO, MessageSchedulerDTO> scheduleRecord) {
    KeyValueStore<ScheduleKeyDTO, MessageSchedulerDTO> scheduleStore =
        context.getStateStore(
            tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES.getStorename()));
    if (scheduleRecord.value() != null) {
      scheduleStore.put(scheduleRecord.key(), scheduleRecord.value());
    } else {
      scheduleStore.delete(scheduleRecord.key());
    }
  }
}
