package nl.qunit.bpmnmeister.engine.pd;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ScheduleProcessor
    implements Processor<ScheduledKey, MessageScheduler, Object, SchedulableMessage> {

  public static final int SCHEDULE_INTERVAL = 100;
  private ProcessorContext<Object, SchedulableMessage> context;
  private final Clock clock;

  public ScheduleProcessor(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void init(ProcessorContext<Object, SchedulableMessage> context) {
    this.context = context;
    context.schedule(
        Duration.ofMillis(SCHEDULE_INTERVAL),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp -> {
          long startTime = System.currentTimeMillis();
          KeyValueStore<ScheduledKey, MessageScheduler> scheduleStore =
              context.getStateStore(Stores.SCHEDULES_STORE_NAME);
          try (KeyValueIterator<ScheduledKey, MessageScheduler> all = scheduleStore.all()) {
            all.forEachRemaining(
                scheduledKeyValue -> {
                  ScheduledKey scheduledKey = scheduledKeyValue.key;
                  MessageScheduler scheduleCommand = scheduledKeyValue.value;
                  if (scheduleCommand != null) {
                    MessageScheduler updatedScheduleCommand =
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
  public void process(Record<ScheduledKey, MessageScheduler> scheduleRecord) {
    KeyValueStore<ScheduledKey, MessageScheduler> scheduleStore =
        context.getStateStore(Stores.SCHEDULES_STORE_NAME);
    if (scheduleRecord.value() != null) {
      scheduleStore.put(scheduleRecord.key(), scheduleRecord.value());
    } else {
      scheduleStore.delete(scheduleRecord.key());
    }
  }
}
