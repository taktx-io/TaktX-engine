/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pd;

import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.SchedulableMessageDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.TimeBucket;
import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.UUID;
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
        Duration.ofMillis(500),
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
              SchedulableMessageDTO message = schedule.getMessage();
              UUID processInstanceKey = message.getProcessInstanceKey();
              if (processInstanceKey == null) {
                processInstanceKey = UUID.randomUUID(); // Assign a new UUID if not set
              }
              context.forward(new Record<>(processInstanceKey, message, key.getTime()));
              iterator.remove();
            } else {
              break;
            }
          }

          if (now >= currentWindowEnd) {
            if (!upcomingSchedules.isEmpty()) {
              log.error(
                  "Missed schedules in time bucket {} {}", timeBucket.getName(), upcomingSchedules);
            }

            long previousWindowEnd = currentWindowEnd;
            currentWindowEnd = currentWindowEnd + timeBucket.getPeriodMs();
            fillNextUpcomingSchedules(previousWindowEnd, currentWindowEnd);
          }
        });
  }

  private void fillNextUpcomingSchedules(long timestamp, long until) {
    try (KeyValueIterator<ScheduleKeyDTO, MessageScheduleDTO> all = store.all()) {
      all.forEachRemaining(
          entry -> {
            MessageScheduleDTO schedule = entry.value;
            Long nextExecutionTime = schedule.getNextExecutionTime(timestamp);
            while (nextExecutionTime != null && nextExecutionTime < until) {
              log.info(
                  "Adding schedule {} for key {} at time {}",
                  schedule,
                  entry.key,
                  nextExecutionTime);
              upcomingSchedules.put(new TimedScheduleKey(nextExecutionTime, entry.key), schedule);
              nextExecutionTime = schedule.getNextExecutionTime(nextExecutionTime);
            }
          });
    }
  }

  public void process(ScheduleKeyDTO scheduleKey, MessageScheduleDTO schedule, long now) {
    if (schedule != null) {
      store.put(scheduleKey, schedule);
      Long nextExecutionTime = schedule.getNextExecutionTime(now);
      while (nextExecutionTime != null && nextExecutionTime <= currentWindowEnd) {
        TimedScheduleKey key = new TimedScheduleKey(nextExecutionTime, scheduleKey);
        var existing = upcomingSchedules.put(key, schedule);
        if (existing != null) {
          log.error("overwriting existing schedule {} {}", key, existing);
        }
        nextExecutionTime = schedule.getNextExecutionTime(nextExecutionTime);
      }
    } else {
      store.delete(scheduleKey);
      upcomingSchedules.entrySet().stream()
          .filter(e -> e.getKey().getScheduleKey().equals(scheduleKey))
          .forEach(e -> upcomingSchedules.remove(e.getKey()));
    }
  }
}
