package nl.qunit.bpmnmeister.engine.pd;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import nl.qunit.bpmnmeister.pi.ProcessInstanceStartCommand;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import nl.qunit.bpmnmeister.scheduler.ScheduleStartCommand;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class ScheduleProcessor
    implements Processor<ScheduleKey, ScheduleStartCommand, String, ProcessInstanceStartCommand> {
  private ProcessorContext<String, ProcessInstanceStartCommand> context;
  private Clock clock;

  public ScheduleProcessor(Clock clock) {
    this.clock = clock;
  }

  @Override
  public void init(ProcessorContext<String, ProcessInstanceStartCommand> context) {
    this.context = context;
    context.schedule(
        Duration.ofSeconds(1),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp -> {
          KeyValueStore<ScheduleKey, ScheduleStartCommand> scheduleStore =
              context.getStateStore(Stores.SCHEDULES_STORE_NAME);
          scheduleStore
              .all()
              .forEachRemaining(
                  scheduleKeyValue -> {
                    ScheduleKey scheduleKey = scheduleKeyValue.key;
                    ScheduleStartCommand scheduleCommand = scheduleKeyValue.value;
                    if (scheduleCommand != null) {
                      ScheduleStartCommand updatedScheduleCommand =
                          scheduleCommand.evaluate(
                              Instant.now(clock),
                              processInstanceStartCommands ->
                                  processInstanceStartCommands.forEach(
                                      processInstanceStartCommand ->
                                          context.forward(
                                              new Record<>(
                                                  processInstanceStartCommand
                                                      .getProcessDefinitionId(),
                                                  processInstanceStartCommand,
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
  public void process(Record<ScheduleKey, ScheduleStartCommand> record) {
    KeyValueStore<ScheduleKey, ScheduleStartCommand> stateStore =
        context.getStateStore(Stores.SCHEDULES_STORE_NAME);
    if (record.value() != null) {
      stateStore.put(record.key(), record.value());
    } else {
      stateStore.delete(record.key());
    }
  }
}
