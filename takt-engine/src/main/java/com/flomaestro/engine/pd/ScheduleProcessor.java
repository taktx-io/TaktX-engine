package com.flomaestro.engine.pd;

import com.flomaestro.engine.generic.TenantNamespaceNameWrapper;
import com.flomaestro.takt.dto.v_1_0_0.MessageScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.SchedulableMessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.TimeBucket;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ScheduleProcessor
    implements Processor<ScheduleKeyDTO, MessageScheduleDTO, Object, SchedulableMessageDTO> {

  private static final int SCHEDULE_INTERVAL_UPCOMING = 100;
  private static final int SCHEDULE_INTERVAL_SECOND = 1000;
  private static final int SCHEDULE_INTERVAL_MINUTE = 60 * SCHEDULE_INTERVAL_SECOND;
  private static final int SCHEDULE_INTERVAL_HOUR = 60 * SCHEDULE_INTERVAL_MINUTE;
  private static final int SCHEDULE_INTERVAL_DAY = 24 * SCHEDULE_INTERVAL_HOUR;
  private static final int SCHEDULE_INTERVAL_WEEKLY = 7 * SCHEDULE_INTERVAL_DAY;

  private ProcessorContext<Object, SchedulableMessageDTO> context;
  private final Clock clock;
  private final TenantNamespaceNameWrapper tenantNamespaceNameWrapper;
  private final ConcurrentSkipListMap<TimedScheduleKeyDTO, MessageScheduleDTO> upcomingSchedules = new ConcurrentSkipListMap<>(
      Comparator.comparingLong(TimedScheduleKeyDTO::time));
  private KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> scheduleStoreSecond;
  private KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> scheduleStoreMinute;
  private KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> scheduleStoreHourly;
  private KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> scheduleStoreDaily;
  private KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> scheduleStoreWeekly;

  public ScheduleProcessor(Clock clock,
      TenantNamespaceNameWrapper tenantNamespaceNameWrapper
  ) {
    this.clock = clock;
    this.tenantNamespaceNameWrapper = tenantNamespaceNameWrapper;
  }

  @Override
  public void init(ProcessorContext<Object, SchedulableMessageDTO> context) {
    this.context = context;

    scheduleStoreSecond = context.getStateStore(
        tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_SECOND.getStorename()));
    scheduleStoreMinute = context.getStateStore(
        tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_MINUTE.getStorename()));
    scheduleStoreHourly = context.getStateStore(
        tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_HOURLY.getStorename()));
    scheduleStoreDaily = context.getStateStore(
        tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_DAILY.getStorename()));
    scheduleStoreWeekly = context.getStateStore(
        tenantNamespaceNameWrapper.getPrefixed(Stores.SCHEDULES_WEEKLY.getStorename()));

    getUpcomingSchedules(scheduleStoreSecond, clock.millis());
    getUpcomingSchedules(scheduleStoreMinute, clock.millis());
    getUpcomingSchedules(scheduleStoreHourly, clock.millis());
    getUpcomingSchedules(scheduleStoreDaily, clock.millis());
    getUpcomingSchedules(scheduleStoreWeekly, clock.millis());

    scheduleProcessBucket(scheduleStoreSecond, SCHEDULE_INTERVAL_SECOND);
    scheduleProcessBucket(scheduleStoreMinute, SCHEDULE_INTERVAL_MINUTE);
    scheduleProcessBucket(scheduleStoreHourly, SCHEDULE_INTERVAL_HOUR);
    scheduleProcessBucket(scheduleStoreDaily, SCHEDULE_INTERVAL_DAY);
    scheduleProcessBucket(scheduleStoreWeekly, SCHEDULE_INTERVAL_WEEKLY);

    processUpcomingSchedules(context);
  }

  private void scheduleProcessBucket(final KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> scheduleStore,
      int scheduleInterval) {
    context.schedule(Duration.ofMillis(scheduleInterval),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp -> {
          getUpcomingSchedules(scheduleStore, clock.millis());
        });
  }

  private void getUpcomingSchedules(KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> scheduleStore, long timestamp) {
    try (KeyValueIterator<ScheduleKeyDTO, MessageScheduleDTO> all = scheduleStore.all()) {
      all.forEachRemaining(entry -> {
        ScheduleKeyDTO key = entry.key;
        MessageScheduleDTO value = entry.value;
        Long time = value.getNextExecutionTime(timestamp);
        if (time != null) {
          upcomingSchedules.put(new TimedScheduleKeyDTO(time, key), value);
        } else {
          scheduleStoreSecond.delete(key);
        }
      });
    }
  }

  private void processUpcomingSchedules(ProcessorContext<Object, SchedulableMessageDTO> context) {
    context.schedule(Duration.ofMillis(SCHEDULE_INTERVAL_UPCOMING), PunctuationType.WALL_CLOCK_TIME, (timestamp) -> {
      Iterator<Entry<TimedScheduleKeyDTO, MessageScheduleDTO>> iterator = upcomingSchedules.entrySet().iterator();
      while (iterator.hasNext()) {
        Entry<TimedScheduleKeyDTO, MessageScheduleDTO> next = iterator.next();
        if (timestamp > next.getKey().time()) {
          iterator.remove();
        } else {
          // Stop iterating when times are not reached yet
          break;
        }
      }
    });
  }

  @Override
  public void process(Record<ScheduleKeyDTO, MessageScheduleDTO> scheduleRecord) {
    KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> scheduleStore = selectScheduleStore(scheduleRecord.key());
    if (scheduleRecord.value() != null) {
      scheduleStore.put(scheduleRecord.key(), scheduleRecord.value());
    } else {
      scheduleStore.delete(scheduleRecord.key());
    }
  }

  private KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> selectScheduleStore(ScheduleKeyDTO key) {
    TimeBucket timeBucket = key.getTimeBucket();
    return switch (timeBucket) {
      case SECOND -> scheduleStoreSecond;
      case MINUTE -> scheduleStoreMinute;
      case HOURLY -> scheduleStoreHourly;
      case DAILY -> scheduleStoreDaily;
      case WEEKLY -> scheduleStoreWeekly;
    };
  }
}
