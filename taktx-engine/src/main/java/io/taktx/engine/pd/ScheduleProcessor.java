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
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.engine.pi.ProcessingStatistics;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.security.AuthorizationTokenException;
import java.nio.charset.StandardCharsets;
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
  private final ProcessingStatistics processingStatistics;
  private final EngineAuthorizationService engineAuthorizationService;
  private final String scheduleTopicName;

  private Map<TimeBucket, BucketProcessor> bucketProcessorMap;

  public ScheduleProcessor(
      Clock clock,
      boolean testProfile,
      BiFunction<
              ProcessorContext<Object, SchedulableMessageDTO>,
              String,
              KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO>>
          scheduleStoreProvider,
      TimeBucket[] timeBuckets,
      ProcessingStatistics processingStatistics,
      EngineAuthorizationService engineAuthorizationService,
      String scheduleTopicName) {
    this.clock = clock;
    this.testProfile = testProfile;
    this.scheduleStoreProvider = scheduleStoreProvider;
    this.timeBuckets = timeBuckets;
    this.processingStatistics = processingStatistics;
    this.engineAuthorizationService = engineAuthorizationService;
    this.scheduleTopicName = scheduleTopicName;
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
    ScheduleKeyDTO scheduleKey = scheduleRecord.key();
    MessageScheduleDTO value = scheduleRecord.value();

    try {
      SigningKeyDTO trustedSigner =
          engineAuthorizationService.authorizeScheduleCommand(
              scheduleRecord.headers(), scheduleKey, value);
      log.info(
          "Accepted schedule command topic='{}' scheduleKey='{}' signerKeyId='{}' signerRole='{}' outcome='accepted' messageType='{}'",
          scheduleTopicName,
          scheduleKey,
          trustedSigner.getKeyId(),
          trustedSigner.effectiveRole(),
          scheduleMessageType(value));
    } catch (AuthorizationTokenException e) {
      log.warn(
          "Rejected schedule command topic='{}' scheduleKey='{}' signerKeyId='{}' outcome='rejected' reason='{}' messageType='{}'",
          scheduleTopicName,
          scheduleKey,
          extractSignerKeyId(scheduleRecord),
          e.getMessage(),
          scheduleMessageType(value));
      return;
    }

    // Record end-to-end latency using Kafka timestamp
    // Use ScheduleKey information to handle tombstone records (null values)
    String scheduleType =
        value != null
            ? scheduleKey.getClass().getSimpleName() + "_CREATE"
            : scheduleKey.getClass().getSimpleName() + "_DELETE";

    processingStatistics.recordScheduleLatency(scheduleRecord.timestamp(), scheduleType);

    BucketProcessor bucketProcessor = bucketProcessorMap.get(scheduleKey.getTimeBucket());
    bucketProcessor.process(scheduleKey, value, clock.millis());
  }

  private static String extractSignerKeyId(
      Record<ScheduleKeyDTO, MessageScheduleDTO> scheduleRecord) {
    var headers = scheduleRecord.headers();
    if (headers == null) {
      return null;
    }
    var header = headers.lastHeader(io.taktx.dto.Constants.HEADER_ENGINE_SIGNATURE);
    if (header == null || header.value() == null) {
      return null;
    }
    String headerValue = new String(header.value(), StandardCharsets.UTF_8);
    int dotIndex = headerValue.indexOf('.');
    return dotIndex >= 0 ? headerValue.substring(0, dotIndex) : headerValue;
  }

  private static String scheduleMessageType(MessageScheduleDTO schedule) {
    if (schedule == null || schedule.getMessage() == null) {
      return null;
    }
    return schedule.getMessage().getClass().getSimpleName();
  }
}
