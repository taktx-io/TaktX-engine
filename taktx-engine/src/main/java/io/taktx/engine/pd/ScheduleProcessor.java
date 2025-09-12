/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd;

import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.SchedulableMessageDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.TimeBucket;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ScheduleProcessor
    implements Processor<ScheduleKeyDTO, MessageScheduleDTO, Object, SchedulableMessageDTO> {

  private final boolean testProfile;
  private final BiFunction<
          ProcessorContext<Object, SchedulableMessageDTO>,
          String,
          KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO>>
      scheduleStoreProvider;
  private final TimeBucket[] timeBuckets;
  private final Clock clock;

  private Map<TimeBucket, BucketProcessor> bucketProcessorMap;

  public ScheduleProcessor(
      Clock clock,
      boolean testProfile,
      BiFunction<
              ProcessorContext<Object, SchedulableMessageDTO>,
              String,
              KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO>>
          scheduleStoreProvider,
      TimeBucket[] timeBuckets) {
    this.clock = clock;
    this.testProfile = testProfile;
    this.scheduleStoreProvider = scheduleStoreProvider;
    this.timeBuckets = timeBuckets;
  }

  @Override
  public void init(ProcessorContext<Object, SchedulableMessageDTO> context) {
    this.bucketProcessorMap = new HashMap<>();

    long now = clock.millis();

    for (TimeBucket timeBucket : timeBuckets) {
      KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> store =
          scheduleStoreProvider.apply(context, timeBucket.getName());
      BucketProcessor bucketProcessor = new BucketProcessor(timeBucket, store, clock, testProfile);
      bucketProcessorMap.put(timeBucket, bucketProcessor);

      bucketProcessor.init(context, now);
    }
  }

  @Override
  public void process(Record<ScheduleKeyDTO, MessageScheduleDTO> scheduleRecord) {
    log.info("Processing schedule record {}", scheduleRecord.key());
    ScheduleKeyDTO scheduleKey = scheduleRecord.key();
    MessageScheduleDTO value = scheduleRecord.value();

    BucketProcessor bucketProcessor = bucketProcessorMap.get(scheduleKey.getTimeBucket());
    bucketProcessor.process(scheduleKey, value, clock.millis());
  }
}
