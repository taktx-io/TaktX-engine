package nl.qunit.bpmnmeister.engine.pd;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.scheduler.ScheduleCommand;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ScheduleProcessor
    implements Processor<ScheduleKey, ScheduleCommand, ProcessInstanceKey, ProcessInstanceTrigger> {
  private ProcessorContext<ProcessInstanceKey, ProcessInstanceTrigger> context;
  private Clock clock;

  public ScheduleProcessor(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void init(ProcessorContext<ProcessInstanceKey, ProcessInstanceTrigger> context) {
    this.context = context;
    context.schedule(
        Duration.ofSeconds(1),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp -> {
          KeyValueStore<ScheduleKey, ScheduleCommand> scheduleStore =
              context.getStateStore(Stores.SCHEDULES_STORE_NAME);
          scheduleStore
              .all()
              .forEachRemaining(
                  scheduleKeyValue -> {
                    ScheduleKey scheduleKey = scheduleKeyValue.key;
                    ScheduleCommand scheduleCommand = scheduleKeyValue.value;
                    if (scheduleCommand != null) {
                      ScheduleCommand updatedScheduleCommand =
                          scheduleCommand.evaluate(
                              Instant.now(clock),
                              triggers ->
                                  triggers.forEach(
                                      trigger ->
                                          context.forward(
                                              new Record<>(
                                                  new ProcessInstanceKey(null),
                                                  trigger,
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
  public void process(Record<ScheduleKey, ScheduleCommand> record) {
    KeyValueStore<ScheduleKey, ScheduleCommand> stateStore =
        context.getStateStore(Stores.SCHEDULES_STORE_NAME);
    if (record.value() != null) {
      stateStore.put(record.key(), record.value());
    } else {
      stateStore.delete(record.key());
    }
  }
}
