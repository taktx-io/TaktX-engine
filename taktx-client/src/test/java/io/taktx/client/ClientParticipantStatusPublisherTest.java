/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.SecurityParticipantDescriptor;
import io.taktx.dto.StatusVerificationLevel;
import io.taktx.serdes.ParticipantStatusProtoMapper;
import io.taktx.util.TaktPropertiesHelper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClientParticipantStatusPublisherTest {

  private static final long NOW_MS = Instant.parse("2026-05-24T10:15:30Z").toEpochMilli();

  private TaktPropertiesHelper propertiesHelper;
  private Clock clock;

  @BeforeEach
  void setUp() {
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    properties.setProperty("taktx.engine.tenant-id", "tenant");
    properties.setProperty("taktx.engine.namespace", "default");
    propertiesHelper = new TaktPropertiesHelper(properties);
    clock = Clock.fixed(Instant.ofEpochMilli(NOW_MS), ZoneOffset.UTC);
  }

  @Test
  void evaluate_openModeProtectedParticipantFullySigningReady_isReadyWithNoReasons() {
    ParticipantStatusDTO status =
        publisher(runtimeDescriptor(), false, true, true, true).evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getMismatchReasons()).isEmpty();
    assertThat(status.getStatusVerificationLevel())
        .isEqualTo(StatusVerificationLevel.LOCALLY_VERIFIED_STATUS);
    assertThat(status.getStartedAt()).isEqualTo(NOW_MS);
    assertThat(status.getLastSeenAt()).isEqualTo(NOW_MS);
    assertThat(status.getStatusExpiresAt())
        .isEqualTo(NOW_MS + ClientParticipantStatusPublisher.STATUS_TTL_MS);
    assertThat(status.getParticipantInstanceId())
        .startsWith("tenant.default.client@")
        .contains("#");
  }

  @Test
  void evaluate_openModeWithSigningGaps_reportsNonBlockingWarningsButStaysReady() {
    ParticipantStatusDTO status =
        publisher(runtimeDescriptor(), false, false, false, false).evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getMismatchReasons()).hasSize(1);
    assertThat(status.getMismatchReasons().getFirst().getCode())
        .isEqualTo(ClientParticipantStatusPublisher.SIGNATURE_MISSING);
    assertThat(status.getMismatchReasons().getFirst().getMetadata())
        .containsEntry("severity", "WARNING");
  }

  @Test
  void evaluate_openModeKeyPublishedButNotCountersigned_warnsRegistrationSignatureMissing() {
    ParticipantStatusDTO status =
        publisher(runtimeDescriptor(), false, true, true, false).evaluateCurrentStatus();

    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getMismatchReasons().getFirst().getCode())
        .isEqualTo(ClientParticipantStatusPublisher.ENGINE_KEY_REGISTRATION_SIGNATURE_MISSING);
    assertThat(status.getMismatchReasons().getFirst().getMetadata())
        .containsEntry("severity", "WARNING");
  }

  @Test
  void evaluate_anchoredModeFullySigningReady_isReady() {
    ParticipantStatusDTO status =
        publisher(runtimeDescriptor(), true, true, true, true).evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
    assertThat(status.getMismatchReasons()).isEmpty();
  }

  @Test
  void evaluate_anchoredModeWithSigningGap_isMismatchAndNotReady() {
    ParticipantStatusDTO status =
        publisher(runtimeDescriptor(), true, true, false, false).evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(ParticipantEffectiveState.MISMATCH);
    assertThat(status.isReadyForDataPlane()).isFalse();
    assertThat(status.getMismatchReasons().getFirst().getCode())
        .isEqualTo(ClientParticipantStatusPublisher.ENGINE_SIGNING_UNAVAILABLE);
    assertThat(status.getMismatchReasons().getFirst().getMetadata())
        .containsEntry("severity", "ERROR");
  }

  @Test
  void evaluate_openModeDefaultsToReady() {
    // OPEN mode (anchored=false) → ready regardless of signing
    ParticipantStatusDTO status =
        publisher(runtimeDescriptor(), false, true, true, true).evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isTrue();
  }

  @Test
  void evaluate_securityObserverOnly_reportsPresentButNotDataPlane() {
    ParticipantStatusDTO status =
        publisher(observerDescriptor(), true, false, false, false).evaluateCurrentStatus();

    assertThat(status.getEffectiveState()).isEqualTo(ParticipantEffectiveState.READY);
    assertThat(status.isReadyForDataPlane()).isFalse();
    assertThat(status.getMismatchReasons()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishCurrentStatus_sendsProtoEncodedStatusToPrefixedTopicKeyedByInstanceId()
      throws Exception {
    Producer<String, byte[]> producer = mock(Producer.class);
    ClientParticipantStatusPublisher publisher =
        new ClientParticipantStatusPublisher(
            propertiesHelper,
            runtimeDescriptor(),
            false, // OPEN
            () -> true,
            () -> true,
            () -> true,
            clock,
            producer);

    ParticipantStatusDTO published = publisher.publishCurrentStatus();

    ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(captor.capture());
    verify(producer).flush();

    ProducerRecord<String, byte[]> record = captor.getValue();
    assertThat(record.topic()).isEqualTo("tenant.default.taktx-participant-status");
    assertThat(record.key()).isEqualTo(published.getParticipantInstanceId());

    ParticipantStatusDTO roundTripped =
        ParticipantStatusProtoMapper.toDto(
            io.taktx.proto.ParticipantStatusMessage.parseFrom(record.value()));
    assertThat(roundTripped.getParticipantId())
        .isEqualTo("tenant.default.client"); // explicit descriptor in test
    assertThat(roundTripped.getEffectiveState()).isEqualTo(ParticipantEffectiveState.READY);
    assertThat(roundTripped.isReadyForDataPlane()).isTrue();
  }

  @Test
  void evaluate_currentSigningKeyIdIsIncludedWhenSupplied() {
    ParticipantStatusDTO status =
        new ClientParticipantStatusPublisher(
                propertiesHelper,
                runtimeDescriptor(),
                false,
                () -> true,
                () -> true,
                () -> true,
                () -> "engine-a3f2b1c4",
                clock)
            .evaluateCurrentStatus();

    assertThat(status.getCurrentSigningKeyId()).isEqualTo("engine-a3f2b1c4");
  }

  @Test
  void evaluate_currentSigningKeyIdIsNullWhenNotSupplied() {
    ParticipantStatusDTO status =
        publisher(runtimeDescriptor(), false, true, true, true).evaluateCurrentStatus();

    assertThat(status.getCurrentSigningKeyId()).isNull();
  }

  private ClientParticipantStatusPublisher publisher(
      SecurityParticipantDescriptor descriptor,
      boolean anchored,
      boolean signingConfigured,
      boolean keyPublished,
      boolean keyCountersigned) {
    return new ClientParticipantStatusPublisher(
        propertiesHelper,
        descriptor,
        anchored,
        (BooleanSupplier) () -> signingConfigured,
        (BooleanSupplier) () -> keyPublished,
        (BooleanSupplier) () -> keyCountersigned,
        clock);
  }

  private static SecurityParticipantDescriptor runtimeDescriptor() {
    return new SecurityParticipantDescriptor(
        "tenant.default.client",
        ParticipantKind.CLIENT,
        Set.of(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT),
        "generic-client");
  }

  private static SecurityParticipantDescriptor observerDescriptor() {
    return new SecurityParticipantDescriptor(
        "tenant.default.observer",
        ParticipantKind.CLIENT,
        Set.of(ParticipantCapability.SECURITY_OBSERVER),
        "observer");
  }
}
