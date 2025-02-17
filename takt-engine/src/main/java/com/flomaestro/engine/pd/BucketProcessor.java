package com.flomaestro.engine.pd;

import com.flomaestro.takt.dto.v_1_0_0.MessageScheduleDTO;
import com.flomaestro.takt.dto.v_1_0_0.SchedulableMessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.TimeBucket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class BucketProcessor {

  private final TimeBucket timeBucket;
  private final KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> store;
  private final Clock clock;
  private final boolean testProfile;
  private final ConcurrentSkipListMap<TimedScheduleKey, MessageScheduleDTO> upcomingSchedules;
  private long currentWindowEnd = 0;

  public BucketProcessor(
      TimeBucket timeBucket,
      KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> store,
      Clock clock,
      boolean testProfile) {
    this.timeBucket = timeBucket;
    this.store = store;
    this.clock = clock;
    this.testProfile = testProfile;

    this.upcomingSchedules = new ConcurrentSkipListMap<>(TimedScheduleKey::compareScheduleKey);
  }

  public void init(ProcessorContext<Object, SchedulableMessageDTO> context, long initialStartTime) {
    log.info("Initializing bucket processor for time bucket {}", timeBucket.getName());

    currentWindowEnd = initialStartTime + timeBucket.getPeriodMs();

    fillNextUpcomingSchedules(initialStartTime, currentWindowEnd);

    context.schedule(
        Duration.ofMillis(200),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp -> {
          long now = testProfile ? clock.millis() : timestamp;

          Iterator<Entry<TimedScheduleKey, MessageScheduleDTO>> iterator =
              upcomingSchedules.entrySet().iterator();
          while (iterator.hasNext()) {
            Entry<TimedScheduleKey, MessageScheduleDTO> entry = iterator.next();
            TimedScheduleKey key = entry.getKey();
            MessageScheduleDTO schedule = entry.getValue();
            if (key.getTime() < now) {
              log.info(
                  "Forwarding schedule {} {} {} {}",
                  key,
                  schedule,
                  Instant.ofEpochMilli(key.getTime()).toString(),
                  Instant.ofEpochMilli(now).toString());
              SchedulableMessageDTO message = schedule.getMessage();
              context.forward(
                  new Record<>(message.getProcessInstanceKey(), message, key.getTime()));
              iterator.remove();
            } else {
              log.info(
                  "Not forwarding schedule {} {} {} {}",
                  key,
                  schedule,
                  Instant.ofEpochMilli(key.getTime()).toString(),
                  Instant.ofEpochMilli(now).toString());
              break;
            }
          }

          if (now >= currentWindowEnd) {
            if (!upcomingSchedules.isEmpty()) {
              log.error(
                  "Missed schedules in time bucket {} {}", timeBucket.getName(), upcomingSchedules);
            }

            long previousWindowEnd = currentWindowEnd;
            currentWindowEnd = now + timeBucket.getPeriodMs();
            fillNextUpcomingSchedules(previousWindowEnd, currentWindowEnd);
          }
        });
  }

  private void fillNextUpcomingSchedules(long timestamp, long until) {
    log.info(
        "Filling upcoming schedules for time bucket {} from {} to {}",
        timeBucket.getName(),
        Instant.ofEpochMilli(timestamp).toString(),
        Instant.ofEpochMilli(until).toString());
    try (KeyValueIterator<ScheduleKeyDTO, MessageScheduleDTO> all = store.all()) {
      all.forEachRemaining(
          entry -> {
            MessageScheduleDTO schedule = entry.value;
            Long nextExecutionTime = schedule.getNextExecutionTime(timestamp);
            while (nextExecutionTime != null && nextExecutionTime < until) {
              log.info(
                  "Adding schedule time {} to upcoming {} {}",
                  Instant.ofEpochMilli(nextExecutionTime).toString(),
                  entry.key,
                  schedule);
              upcomingSchedules.put(new TimedScheduleKey(nextExecutionTime, entry.key), schedule);
              nextExecutionTime = schedule.getNextExecutionTime(nextExecutionTime);
            }
          });
    }
  }

  public void process(ScheduleKeyDTO scheduleKey, MessageScheduleDTO schedule, long now) {
    log.info(
        "Processing schedule {} {} for time {} windowend {}",
        scheduleKey,
        schedule,
        Instant.ofEpochMilli(now).toString(),
        Instant.ofEpochMilli(currentWindowEnd).toString());
    if (schedule != null) {
      store.put(scheduleKey, schedule);
      Long nextExecutionTime = schedule.getNextExecutionTime(now);
      while (nextExecutionTime != null && nextExecutionTime < currentWindowEnd) {
        TimedScheduleKey key = new TimedScheduleKey(nextExecutionTime, scheduleKey);
        log.info(
            "Adding schedule time {} to current window ending {} upcoming {} {}",
            Instant.ofEpochMilli(nextExecutionTime).toString(),
            Instant.ofEpochMilli(currentWindowEnd).toString(),
            key,
            schedule);
        var existing = upcomingSchedules.put(key, schedule);
        if (existing != null) {
          log.error("overwriting existing schedule {} {}", key, existing);
        }
        nextExecutionTime = schedule.getNextExecutionTime(nextExecutionTime);
      }
    } else {
      log.info("Deleting schedule {}", scheduleKey);
      store.delete(scheduleKey);
      upcomingSchedules.entrySet().stream()
          .filter(e -> e.getKey().getScheduleKey().equals(scheduleKey))
          .forEach(e -> upcomingSchedules.remove(e.getKey()));
    }
  }
}
