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
import io.taktx.engine.dlq.DlqObservabilityService;
import io.taktx.engine.pi.ProcessingStatistics;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.security.ProtectedDataPlaneParticipationGuard;
import io.taktx.engine.security.VerificationCore;
import io.taktx.security.AuthorizationTokenException;
import java.time.Clock;
import java.util.EnumMap;
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

  private static final String SCHEDULE_COMMANDS_TOPIC_GROUP = "schedule-commands";

  private final boolean testProfile;
  private final BiFunction<
          ProcessorContext<Object, SchedulableMessageDTO>,
          String,
          KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO>>
      scheduleStoreProvider;
  private final TimeBucket[] timeBuckets;
  private final Clock clock;
  private final ProcessingStatistics processingStatistics;
  private final String scheduleTopicName;
  private final SecurityServices securityServices;

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
      String scheduleTopicName,
      SecurityServices securityServices) {
    this.clock = clock;
    this.testProfile = testProfile;
    this.scheduleStoreProvider = scheduleStoreProvider;
    this.timeBuckets = timeBuckets;
    this.processingStatistics = processingStatistics;
    this.scheduleTopicName = scheduleTopicName;
    this.securityServices = securityServices;
  }

  @Override
  public void init(ProcessorContext<Object, SchedulableMessageDTO> context) {
    this.bucketProcessorMap = new EnumMap<>(TimeBucket.class);

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
          securityServices
              .engineAuthorizationService()
              .authorizeScheduleCommand(scheduleRecord.headers(), scheduleKey, value);
      if (trustedSigner != null) {
        log.info(
            "Accepted schedule command topic='{}' scheduleKey='{}' signerKeyId='{}' signerRole='{}' outcome='accepted' messageType='{}'",
            scheduleTopicName,
            scheduleKey,
            trustedSigner.getKeyId(),
            trustedSigner.effectiveRole(),
            scheduleMessageType(value));
      } else {
        log.info(
            "Accepted schedule command topic='{}' scheduleKey='{}' outcome='accepted' (security disabled) messageType='{}'",
            scheduleTopicName,
            scheduleKey,
            scheduleMessageType(value));
      }
    } catch (AuthorizationTokenException e) {
      log.warn(
          "Rejected schedule command topic='{}' scheduleKey='{}' signerKeyId='{}' outcome='rejected' reason='{}' messageType='{}'",
          scheduleTopicName,
          scheduleKey,
          extractSignerKeyId(scheduleRecord),
          e.getMessage(),
          scheduleMessageType(value));
      securityServices
          .dlqObservabilityService()
          .recordExcludedTopicFailure(SCHEDULE_COMMANDS_TOPIC_GROUP);
      return;
    }

    if (shouldBlockProtectedDataPlane(scheduleKey, value)) {
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
    try {
      bucketProcessor.process(scheduleKey, value, clock.millis());
    } catch (Exception e) {
      // schedule-commands is an engine-internal topic — the engine itself produces to it.
      // A processing failure here is an engine defect, not a user-correctable replay situation.
      // Log as a structured incident and skip the record to keep the stream thread alive.
      log.error(
          "INCIDENT schedule-command processing failure — no DLQ (engine-internal topic)."
              + " topic='{}' scheduleKey='{}' messageType='{}' cause='{}'",
          scheduleTopicName,
          scheduleKey,
          scheduleMessageType(value),
          e.getMessage(),
          e);
      // DLQ-018A: increment counter so dashboards can track excluded-topic failures.
      securityServices
          .dlqObservabilityService()
          .recordExcludedTopicFailure(SCHEDULE_COMMANDS_TOPIC_GROUP);
    }
  }

  private boolean shouldBlockProtectedDataPlane(
      ScheduleKeyDTO scheduleKey, MessageScheduleDTO schedule) {
    if (securityServices.protectedDataPlaneParticipationGuard() == null) {
      return false;
    }
    ProtectedDataPlaneParticipationGuard.Decision decision =
        securityServices.protectedDataPlaneParticipationGuard().evaluate();
    if (decision.permitted()) {
      return false;
    }
    log.warn(
        "Rejected schedule command topic='{}' scheduleKey='{}' outcome='rejected' reasonHint='{}' reason='{}' messageType='{}'",
        scheduleTopicName,
        scheduleKey,
        decision.reasonHint(),
        decision.reasonText(),
        scheduleMessageType(schedule));
    securityServices
        .dlqObservabilityService()
        .recordExcludedTopicFailure(SCHEDULE_COMMANDS_TOPIC_GROUP);
    return true;
  }

  public record SecurityServices(
      EngineAuthorizationService engineAuthorizationService,
      DlqObservabilityService dlqObservabilityService,
      ProtectedDataPlaneParticipationGuard protectedDataPlaneParticipationGuard) {}

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
    return VerificationCore.extractKeyId(header);
  }

  private static String scheduleMessageType(MessageScheduleDTO schedule) {
    if (schedule == null || schedule.getMessage() == null) {
      return null;
    }
    return schedule.getMessage().getClass().getSimpleName();
  }
}
