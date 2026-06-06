/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.config;

import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.proto.ParticipantStatusMessage;
import io.taktx.security.ParticipantStatusSupport;
import io.taktx.serdes.ParticipantStatusProtoMapper;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global-store processor for the namespace-local {@code taktx-participant-status} topic.
 *
 * <p>This processor maintains latest-state participant telemetry and applies required status TTL /
 * structural validation semantics.
 */
public class ParticipantStatusProcessor implements Processor<String, byte[], Void, Void> {

  private static final Logger log = LoggerFactory.getLogger(ParticipantStatusProcessor.class);

  private final ParticipantStatusStore participantStatusStore;

  public ParticipantStatusProcessor(ParticipantStatusStore participantStatusStore) {
    this.participantStatusStore = participantStatusStore;
  }

  @Override
  public void init(ProcessorContext<Void, Void> context) {
    // nothing to initialize
  }

  @Override
  public void process(Record<String, byte[]> rec) {
    if (rec.key() == null || rec.key().isBlank()) {
      log.warn("Ignoring participant status record with blank key");
      return;
    }

    if (rec.value() == null) {
      participantStatusStore.remove(rec.key());
      log.info("Participant status cleared from tombstone record: key={}", rec.key());
      return;
    }

    try {
      ParticipantStatusDTO status =
          ParticipantStatusProtoMapper.toDto(ParticipantStatusMessage.parseFrom(rec.value()));
      ParticipantStatusDTO validated = ParticipantStatusSupport.requireValid(status);
      participantStatusStore.update(rec.key(), validated);
      log.debug(
          "Participant status updated: key={} participantId={} participantInstanceId={} effectiveState={} readyForDataPlane={}",
          rec.key(),
          validated.getParticipantId(),
          validated.getParticipantInstanceId(),
          validated.getEffectiveState(),
          validated.isReadyForDataPlane());
    } catch (Exception e) {
      log.warn("Failed to deserialize or validate participant status: {}", e.getMessage());
    }
  }

  @Override
  public void close() {
    // nothing to close
  }
}
