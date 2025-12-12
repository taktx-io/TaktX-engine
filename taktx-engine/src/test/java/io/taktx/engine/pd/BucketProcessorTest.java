/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.FixedRateMessageScheduleDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.OneTimeScheduleDTO;
import io.taktx.dto.SchedulableMessageDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.TimeBucket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.Punctuator;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BucketProcessorTest {

  private Map<ScheduleKeyDTO, MessageScheduleDTO> storeMap;
  private KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> store;
  private ProcessorContext<Object, SchedulableMessageDTO> context;
  private Clock clock;
  private BucketProcessor processor;
  private Punctuator capturedPunctuator;
  private AtomicLong currentTime;

  @BeforeEach
  void setUp() {
    storeMap = new HashMap<>();
    store = mock(KeyValueStore.class);
    context = mock(ProcessorContext.class);
    currentTime = new AtomicLong(1000000L);

    // Create a clock that returns the current test time
    clock =
        new Clock() {
          @Override
          public ZoneId getZone() {
            return ZoneId.systemDefault();
          }

          @Override
          public Clock withZone(ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return Instant.ofEpochMilli(currentTime.get());
          }
        };

    // Mock store behavior
    when(store.get(any())).thenAnswer(inv -> storeMap.get(inv.getArgument(0)));
    doAnswer(
            inv -> {
              storeMap.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(store)
        .put(any(), any());
    when(store.delete(any())).thenAnswer(inv -> storeMap.remove(inv.getArgument(0)));
    when(store.all())
        .thenAnswer(
            inv -> {
              List<org.apache.kafka.streams.KeyValue<ScheduleKeyDTO, MessageScheduleDTO>> list =
                  new ArrayList<>();
              storeMap.forEach((k, v) -> list.add(new org.apache.kafka.streams.KeyValue<>(k, v)));
              return new InMemoryIterator(list);
            });

    // Capture the punctuator
    ArgumentCaptor<Punctuator> punctuatorCaptor = ArgumentCaptor.forClass(Punctuator.class);
    when(context.schedule(
            any(Duration.class), any(PunctuationType.class), punctuatorCaptor.capture()))
        .thenReturn(mock(Cancellable.class));

    processor = new BucketProcessor(TimeBucket.MINUTE, store, clock, true);
    processor.init(context, currentTime.get());

    capturedPunctuator = punctuatorCaptor.getValue();
    assertThat(capturedPunctuator).isNotNull();
  }

  @Test
  void testProcessScheduleAtExactTime() {
    // Create a schedule that fires exactly at a specific time
    long fireTime = currentTime.get() + 30000; // 30 seconds from now
    ScheduleKeyDTO key = createScheduleKey(UUID.randomUUID(), "timer1");
    OneTimeScheduleDTO schedule =
        new OneTimeScheduleDTO(createMessage(UUID.randomUUID()), currentTime.get(), fireTime);

    // Process the schedule
    processor.process(key, schedule, currentTime.get());
    System.out.println("Fire time: " + fireTime + ", Current time: " + currentTime.get());

    // Advance time to just before fire time
    currentTime.set(fireTime - 1);
    System.out.println("Advanced to: " + currentTime.get() + ", Clock: " + clock.millis());
    capturedPunctuator.punctuate(currentTime.get());
    verify(context, never()).forward(any(Record.class));

    // Advance time to exactly fire time - should fire now with <= comparison
    currentTime.set(fireTime);
    System.out.println("Advanced to: " + currentTime.get() + ", Clock: " + clock.millis());
    capturedPunctuator.punctuate(currentTime.get());
    verify(context, times(1)).forward(any(Record.class));
  }

  @Test
  void testMultipleSchedulesAtSameTime() {
    // Create multiple schedules that fire at the same time
    long fireTime = currentTime.get() + 10000;

    for (int i = 0; i < 5; i++) {
      ScheduleKeyDTO key = createScheduleKey(UUID.randomUUID(), "timer" + i);
      OneTimeScheduleDTO schedule =
          new OneTimeScheduleDTO(createMessage(UUID.randomUUID()), currentTime.get(), fireTime);
      processor.process(key, schedule, currentTime.get());
    }

    // Advance time to fire time
    currentTime.set(fireTime);
    capturedPunctuator.punctuate(currentTime.get());

    // All 5 schedules should fire
    verify(context, times(5)).forward(any(Record.class));
  }

  @Test
  void testRecurringScheduleMultipleExecutions() {
    // Create a recurring schedule (every 5 seconds, 3 times)
    long period = 5000;
    int repetitions = 3;
    ScheduleKeyDTO key = createScheduleKey(UUID.randomUUID(), "recurring");
    FixedRateMessageScheduleDTO schedule =
        new FixedRateMessageScheduleDTO(
            createMessage(UUID.randomUUID()), period, repetitions, currentTime.get());

    processor.process(key, schedule, currentTime.get());

    // First execution
    currentTime.addAndGet(period);
    capturedPunctuator.punctuate(currentTime.get());
    verify(context, times(1)).forward(any(Record.class));

    // Second execution
    currentTime.addAndGet(period);
    capturedPunctuator.punctuate(currentTime.get());
    verify(context, times(2)).forward(any(Record.class));

    // Third execution
    currentTime.addAndGet(period);
    capturedPunctuator.punctuate(currentTime.get());
    verify(context, times(3)).forward(any(Record.class));

    // No more executions
    currentTime.addAndGet(period);
    capturedPunctuator.punctuate(currentTime.get());
    verify(context, times(3)).forward(any(Record.class)); // Still only 3
  }

  @Test
  void testWindowBoundaryProcessing() {
    // Create a schedule that fires exactly at window boundary
    long windowEnd = currentTime.get() + TimeBucket.MINUTE.getPeriodMs();
    ScheduleKeyDTO key = createScheduleKey(UUID.randomUUID(), "boundary");
    OneTimeScheduleDTO schedule =
        new OneTimeScheduleDTO(createMessage(UUID.randomUUID()), currentTime.get(), windowEnd);

    processor.process(key, schedule, currentTime.get());

    // Advance time to window boundary
    currentTime.set(windowEnd);
    capturedPunctuator.punctuate(currentTime.get());

    // Schedule should fire at boundary with <= comparison
    verify(context, times(1)).forward(any(Record.class));
  }

  @Test
  void testLateSchedulesProcessedBeforeWindowTransition() {
    // Create schedules that should have fired but didn't (simulating delay)
    long fireTime1 = currentTime.get() + 10000;
    long fireTime2 = currentTime.get() + 20000;
    long windowEnd = currentTime.get() + TimeBucket.MINUTE.getPeriodMs();

    ScheduleKeyDTO key1 = createScheduleKey(UUID.randomUUID(), "late1");
    ScheduleKeyDTO key2 = createScheduleKey(UUID.randomUUID(), "late2");

    OneTimeScheduleDTO schedule1 =
        new OneTimeScheduleDTO(createMessage(UUID.randomUUID()), currentTime.get(), fireTime1);
    OneTimeScheduleDTO schedule2 =
        new OneTimeScheduleDTO(createMessage(UUID.randomUUID()), currentTime.get(), fireTime2);

    processor.process(key1, schedule1, currentTime.get());
    processor.process(key2, schedule2, currentTime.get());

    // Jump directly to window end without firing schedules
    currentTime.set(windowEnd);
    capturedPunctuator.punctuate(currentTime.get());

    // Both late schedules should be processed before window transition
    verify(context, times(2)).forward(any(Record.class));
  }

  @Test
  void testDeleteSchedule() {
    // Create and add a schedule
    ScheduleKeyDTO key = createScheduleKey(UUID.randomUUID(), "delete-test");
    OneTimeScheduleDTO schedule =
        new OneTimeScheduleDTO(
            createMessage(UUID.randomUUID()), currentTime.get(), currentTime.get() + 10000);

    processor.process(key, schedule, currentTime.get());

    // Delete the schedule
    processor.process(key, null, currentTime.get());

    // Advance time past when it would have fired
    currentTime.addAndGet(15000);
    capturedPunctuator.punctuate(currentTime.get());

    // Should not fire
    verify(context, never()).forward(any(Record.class));
  }

  @Test
  void testDuplicateScheduleNotOverwritten() {
    // Create a schedule
    UUID processInstanceId = UUID.randomUUID();
    ScheduleKeyDTO key = createScheduleKey(processInstanceId, "duplicate-test");
    OneTimeScheduleDTO schedule1 =
        new OneTimeScheduleDTO(
            createMessage(processInstanceId), currentTime.get(), currentTime.get() + 10000);

    processor.process(key, schedule1, currentTime.get());

    // Add the same schedule again (with same key and time)
    processor.process(key, schedule1, currentTime.get());

    // Advance time
    currentTime.addAndGet(10000);
    capturedPunctuator.punctuate(currentTime.get());

    // Should only fire once (not duplicated)
    verify(context, times(1)).forward(any(Record.class));
  }

  @Test
  void testProcessInstanceIdAssignment() {
    // Create a schedule without process instance ID
    ScheduleKeyDTO key = createScheduleKey(UUID.randomUUID(), "no-id");
    ContinueFlowElementTriggerDTO message = new ContinueFlowElementTriggerDTO();
    message.setProcessInstanceId(null); // Explicitly null

    OneTimeScheduleDTO schedule =
        new OneTimeScheduleDTO(message, currentTime.get(), currentTime.get() + 1000);

    processor.process(key, schedule, currentTime.get());

    // Advance time
    currentTime.addAndGet(1000);
    capturedPunctuator.punctuate(currentTime.get());

    // Verify a UUID was assigned
    ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(recordCaptor.capture());
    Record<?, ?> captured = recordCaptor.getValue();
    assertThat(captured.key()).isNotNull();
    assertThat(captured.key()).isInstanceOf(UUID.class);
  }

  @Test
  void testEmptyStoreOptimization() {
    // Create a new processor with empty store
    BucketProcessor emptyProcessor = new BucketProcessor(TimeBucket.MINUTE, store, clock, true);
    ProcessorContext<Object, SchedulableMessageDTO> emptyContext = mock(ProcessorContext.class);

    ArgumentCaptor<Punctuator> punctuatorCaptor = ArgumentCaptor.forClass(Punctuator.class);
    when(emptyContext.schedule(
            any(Duration.class), any(PunctuationType.class), punctuatorCaptor.capture()))
        .thenReturn(mock(Cancellable.class));

    emptyProcessor.init(emptyContext, currentTime.get());
    Punctuator emptyPunctuator = punctuatorCaptor.getValue();

    // Advance to trigger window transition
    currentTime.addAndGet(TimeBucket.MINUTE.getPeriodMs());
    emptyPunctuator.punctuate(currentTime.get());

    // Should not forward anything
    verify(emptyContext, never()).forward(any(Record.class));
  }

  @Test
  void testSchedulesBeyondWindowNotAdded() {
    // Create a schedule beyond the current window
    long beyondWindow = currentTime.get() + TimeBucket.MINUTE.getPeriodMs() + 10000;
    ScheduleKeyDTO key = createScheduleKey(UUID.randomUUID(), "beyond");
    OneTimeScheduleDTO schedule =
        new OneTimeScheduleDTO(createMessage(UUID.randomUUID()), currentTime.get(), beyondWindow);

    processor.process(key, schedule, currentTime.get());

    // The schedule should be in the store
    assertThat(store.get(key)).isNotNull();

    // But not in upcoming schedules yet (beyond current window)
    currentTime.addAndGet(5000);
    capturedPunctuator.punctuate(currentTime.get());
    verify(context, never()).forward(any(Record.class));

    // Move to next window
    currentTime.set(currentTime.get() + TimeBucket.MINUTE.getPeriodMs());
    capturedPunctuator.punctuate(currentTime.get());

    // Now advance to when it should fire
    currentTime.set(beyondWindow);
    capturedPunctuator.punctuate(currentTime.get());
    verify(context, times(1)).forward(any(Record.class));
  }

  @Test
  void testHighLoadManySchedules() {
    // Create many schedules firing at different times
    int scheduleCount = 100;
    for (int i = 0; i < scheduleCount; i++) {
      ScheduleKeyDTO key = createScheduleKey(UUID.randomUUID(), "load-test-" + i);
      OneTimeScheduleDTO schedule =
          new OneTimeScheduleDTO(
              createMessage(UUID.randomUUID()),
              currentTime.get(),
              currentTime.get() + ((i + 1) * 100) // Stagger by 100ms, starting at +100ms
              );
      processor.process(key, schedule, currentTime.get());
    }

    // Advance time through all schedules (now need to go to 100*100 + 100)
    currentTime.addAndGet((scheduleCount + 1) * 100);
    capturedPunctuator.punctuate(currentTime.get());

    // All schedules should fire
    verify(context, times(scheduleCount)).forward(any(Record.class));
  }

  // Helper methods

  private ScheduleKeyDTO createScheduleKey(UUID processInstanceId, String elementId) {
    return new InstanceScheduleKeyDTO(processInstanceId, List.of(1L), elementId, TimeBucket.MINUTE);
  }

  private SchedulableMessageDTO createMessage(UUID processInstanceId) {
    ContinueFlowElementTriggerDTO message =
        new ContinueFlowElementTriggerDTO(processInstanceId, List.of(1L), "flow1", null);
    return message;
  }

  private static class InMemoryIterator
      implements KeyValueIterator<ScheduleKeyDTO, MessageScheduleDTO> {
    private final List<org.apache.kafka.streams.KeyValue<ScheduleKeyDTO, MessageScheduleDTO>> list;
    private int index = 0;

    InMemoryIterator(
        List<org.apache.kafka.streams.KeyValue<ScheduleKeyDTO, MessageScheduleDTO>> list) {
      this.list = list;
    }

    @Override
    public void close() {}

    @Override
    public ScheduleKeyDTO peekNextKey() {
      return hasNext() ? list.get(index).key : null;
    }

    @Override
    public boolean hasNext() {
      return index < list.size();
    }

    @Override
    public org.apache.kafka.streams.KeyValue<ScheduleKeyDTO, MessageScheduleDTO> next() {
      return list.get(index++);
    }
  }
}
