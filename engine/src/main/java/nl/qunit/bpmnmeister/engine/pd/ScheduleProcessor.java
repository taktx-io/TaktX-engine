package nl.qunit.bpmnmeister.engine.pd;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ScheduleProcessor
    implements Processor<ScheduleKey, MessageScheduler, Object, SchedulableMessage> {
  private ProcessorContext<Object, SchedulableMessage> context;
  private final Clock clock;

  public ScheduleProcessor(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void init(ProcessorContext<Object, SchedulableMessage> context) {
    this.context = context;
    context.schedule(
        Duration.ofMillis(100),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp -> {
          KeyValueStore<ScheduleKey, MessageScheduler> scheduleStore =
              context.getStateStore(Stores.SCHEDULES_STORE_NAME);
          scheduleStore
              .all()
              .forEachRemaining(
                  scheduleKeyValue -> {
                    ScheduleKey scheduleKey = scheduleKeyValue.key;
                    MessageScheduler scheduleCommand = scheduleKeyValue.value;
                    if (scheduleCommand != null) {
                      MessageScheduler updatedScheduleCommand =
                          scheduleCommand.evaluate(
                              Instant.now(clock),
                              schedulableMessages ->
                                  schedulableMessages.forEach(
                                      message ->
                                          context.forward(
                                              new Record<>(
                                                  message.getRecordKey(),
                                                  message,
                                                  Instant.now(clock).toEpochMilli()))));
                      if (updatedScheduleCommand != null) {
                        scheduleStore.put(scheduleKey, updatedScheduleCommand);
                      } else {
                        scheduleStore.delete(scheduleKey);
                      }
                    }
                  });
        });
  }

  @Override
  public void process(Record<ScheduleKey, MessageScheduler> scheduleRecord) {
    KeyValueStore<ScheduleKey, MessageScheduler> stateStore =
        context.getStateStore(Stores.SCHEDULES_STORE_NAME);
    if (scheduleRecord.value() != null) {
      stateStore.put(scheduleRecord.key(), scheduleRecord.value());
    } else {
      stateStore.delete(scheduleRecord.key());
    }
  }
}
