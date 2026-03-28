/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.SchedulableMessageDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.TimeBucket;
import java.time.Clock;
import java.time.Duration;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
  private final AtomicLong storeSize = new AtomicLong(0);
  private final AtomicBoolean punctuatorRunning = new AtomicBoolean(false);
  private volatile long currentWindowEnd = 0;

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
        Duration.ofMillis(testProfile ? 100 : 500),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp -> {
          // Guard against overlapping punctuator runs
          if (!punctuatorRunning.compareAndSet(false, true)) {
            log.warn(
                "Punctuator already running for bucket {}, skipping this cycle",
                timeBucket.getName());
            return;
          }

          try {
            long now = testProfile ? clock.millis() : timestamp;
            long windowEnd = currentWindowEnd; // Read volatile once
            long startNanos = System.nanoTime();
            long maxProcessingTimeNs =
                (testProfile ? 80_000_000L : 400_000_000L); // 80% of interval
            int processed = 0;

            // Phase 1: Process due schedules (lock-free using pollFirstEntry)
            while (!upcomingSchedules.isEmpty()) {
              // Check time budget
              if (System.nanoTime() - startNanos > maxProcessingTimeNs) {
                log.warn(
                    "Bucket {} punctuator time budget exceeded, processed {} schedules, {} remaining",
                    timeBucket.getName(),
                    processed,
                    upcomingSchedules.size());
                break;
              }

              Entry<TimedScheduleKey, MessageScheduleDTO> entry = upcomingSchedules.firstEntry();
              if (entry == null) break;

              TimedScheduleKey key = entry.getKey();
              // Changed < to <= to include boundary times
              if (key.getTime() <= now) {
                MessageScheduleDTO schedule = upcomingSchedules.pollFirstEntry().getValue();
                if (schedule != null) {
                  SchedulableMessageDTO message = schedule.getMessage();
                  UUID processInstanceId = message.getProcessInstanceId();
                  if (processInstanceId == null) {
                    processInstanceId = UUID.randomUUID();
                  }
                  context.forward(new Record<>(processInstanceId, message, now));
                  processed++;
                }
              } else {
                break;
              }
            }

            // Phase 2: Window transition
            if (now >= windowEnd) {
              // Process remaining schedules that should have fired before window transition
              while (!upcomingSchedules.isEmpty()) {
                Entry<TimedScheduleKey, MessageScheduleDTO> entry = upcomingSchedules.firstEntry();
                if (entry == null) break;

                TimedScheduleKey key = entry.getKey();
                // Changed < to <= to include boundary times
                if (key.getTime() <= windowEnd) {
                  MessageScheduleDTO schedule = upcomingSchedules.pollFirstEntry().getValue();
                  if (schedule != null) {
                    log.warn(
                        "Processing late schedule {} at time {} in bucket {}",
                        key,
                        now,
                        timeBucket.getName());
                    SchedulableMessageDTO message = schedule.getMessage();
                    UUID processInstanceId = message.getProcessInstanceId();
                    if (processInstanceId == null) {
                      processInstanceId = UUID.randomUUID();
                    }
                    context.forward(new Record<>(processInstanceId, message, now));
                    processed++;
                  }
                } else {
                  break;
                }
              }

              long previousWindowEnd = windowEnd;
              long newWindowEnd = windowEnd + timeBucket.getPeriodMs();

              // Atomic window update
              currentWindowEnd = newWindowEnd;

              fillNextUpcomingSchedules(previousWindowEnd, newWindowEnd);
            }

            // Log performance metrics if processing took significant time
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            if (durationMs > 100) {
              log.warn(
                  "Bucket {} punctuator took {}ms to process {} schedules",
                  timeBucket.getName(),
                  durationMs,
                  processed);
            }
          } finally {
            punctuatorRunning.set(false);
          }
        });
  }

  private void fillNextUpcomingSchedules(long timestamp, long until) {
    // Fast path: skip if store is empty
    if (storeSize.get() == 0) {
      log.debug(
          "Skipping fillNextUpcomingSchedules for bucket {} - store is empty",
          timeBucket.getName());
      return;
    }

    long startNanos = System.nanoTime();
    AtomicLong scheduleCount = new AtomicLong(0);
    AtomicLong executionCount = new AtomicLong(0);

    try (KeyValueIterator<ScheduleKeyDTO, MessageScheduleDTO> all = store.all()) {
      all.forEachRemaining(
          entry -> {
            MessageScheduleDTO schedule = entry.value;
            Long nextExecutionTime = schedule.getNextExecutionTime(timestamp);
            int addedForThisSchedule = 0;
            // Changed < to <= to include boundary times
            while (nextExecutionTime != null && nextExecutionTime <= until) {
              TimedScheduleKey key = new TimedScheduleKey(nextExecutionTime, entry.key);
              // Use putIfAbsent to avoid overwriting existing schedules
              MessageScheduleDTO existing = upcomingSchedules.putIfAbsent(key, schedule);
              if (existing == null) {
                log.debug(
                    "Adding schedule {} for key {} at time {}",
                    schedule,
                    entry.key,
                    nextExecutionTime);
                addedForThisSchedule++;
              } else {
                log.debug("Schedule already exists for key {}, skipping duplicate", key);
              }
              nextExecutionTime = schedule.getNextExecutionTime(nextExecutionTime);
            }
            if (addedForThisSchedule > 0) {
              scheduleCount.incrementAndGet();
              executionCount.addAndGet(addedForThisSchedule);
            }
          });
    }

    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
    long schedCount = scheduleCount.get();
    long execCount = executionCount.get();
    if (durationMs > 100 || execCount > 100) {
      log.warn(
          "fillNextUpcomingSchedules for bucket {} took {}ms, processed {} schedules with {} executions",
          timeBucket.getName(),
          durationMs,
          schedCount,
          execCount);
    } else {
      log.debug(
          "fillNextUpcomingSchedules for bucket {} took {}ms, processed {} schedules with {} executions",
          timeBucket.getName(),
          durationMs,
          schedCount,
          execCount);
    }
  }

  public void process(ScheduleKeyDTO scheduleKey, MessageScheduleDTO schedule, long now) {
    long windowEnd = currentWindowEnd; // Read volatile once

    if (schedule != null) {
      // Track store size
      boolean isNew = store.get(scheduleKey) == null;
      store.put(scheduleKey, schedule);
      if (isNew) {
        storeSize.incrementAndGet();
      }

      Long nextExecutionTime = schedule.getNextExecutionTime(now);
      while (nextExecutionTime != null && nextExecutionTime <= windowEnd) {
        TimedScheduleKey key = new TimedScheduleKey(nextExecutionTime, scheduleKey);
        // Use putIfAbsent to avoid overwriting
        MessageScheduleDTO existing = upcomingSchedules.putIfAbsent(key, schedule);
        if (existing != null) {
          log.debug("Schedule already exists for key {}, skipping duplicate", key);
        }
        nextExecutionTime = schedule.getNextExecutionTime(nextExecutionTime);
      }
    } else {
      // Delete schedule
      MessageScheduleDTO deleted = store.delete(scheduleKey);
      if (deleted != null) {
        storeSize.decrementAndGet();
      }
      // Use removeIf for cleaner, more efficient deletion
      upcomingSchedules.entrySet().removeIf(e -> e.getKey().getScheduleKey().equals(scheduleKey));
    }
  }
}
