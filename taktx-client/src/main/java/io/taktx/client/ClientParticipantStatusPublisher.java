/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.Topics;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantEffectiveState;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.PolicyMismatchReasonDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityParticipantDescriptor;
import io.taktx.security.ParticipantStatusSupport;
import io.taktx.serdes.ParticipantStatusProtoMapper;
import io.taktx.util.TaktPropertiesHelper;
import java.net.InetAddress;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Self-contained scheduled publisher that lets a {@link TaktXClient} self-report its participant
 * security posture to the {@code taktx-participant-status} control-plane topic, mirroring the way
 * the engine publishes its own status via {@code ParticipantStatusPublisher}.
 *
 * <p>Started from {@link TaktXClient#start()} when the client declares {@link
 * ParticipantCapability#PROTECTED_RUNTIME_PARTICIPANT}, it publishes a {@link ParticipantStatusDTO}
 * every {@value #PUBLISH_INTERVAL_MS} ms and stops cleanly when {@link TaktXClient#stop()} is
 * called.
 */
final class ClientParticipantStatusPublisher {

  static final long PUBLISH_INTERVAL_MS = 30_000L;
  static final long STATUS_TTL_MS = 90_000L; // 3× publish interval, matching engine convention

  // Non-blocking informational warnings (OPEN mode) and blocking reasons (ANCHORED mode) share
  // these codes; the WARNING/ERROR distinction is carried in the reason metadata and the
  // effectiveState / readyForDataPlane fields.
  static final String SIGNATURE_MISSING = "SIGNATURE_MISSING";
  static final String ENGINE_SIGNING_UNAVAILABLE = "ENGINE_SIGNING_UNAVAILABLE";
  static final String ENGINE_KEY_REGISTRATION_SIGNATURE_MISSING =
      "ENGINE_KEY_REGISTRATION_SIGNATURE_MISSING";

  private static final Logger log = LoggerFactory.getLogger(ClientParticipantStatusPublisher.class);

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final SecurityParticipantDescriptor participantDescriptor;
  private final Supplier<ObservedPolicySnapshot> observedPolicySupplier;
  private final BooleanSupplier signingConfiguredSupplier;
  private final BooleanSupplier keyPublishedSupplier;
  private final BooleanSupplier keyCountersignedSupplier;
  private final Supplier<String> currentSigningKeyIdSupplier;
  private final Clock clock;
  private final long startedAtMs;
  private final String participantInstanceId;

  private Producer<String, byte[]> producer;
  private ScheduledExecutorService scheduler;

  ClientParticipantStatusPublisher(
      TaktPropertiesHelper taktPropertiesHelper,
      SecurityParticipantDescriptor participantDescriptor,
      Supplier<ObservedPolicySnapshot> observedPolicySupplier,
      BooleanSupplier signingConfiguredSupplier,
      BooleanSupplier keyPublishedSupplier,
      BooleanSupplier keyCountersignedSupplier,
      Clock clock) {
    this(
        taktPropertiesHelper,
        participantDescriptor,
        observedPolicySupplier,
        signingConfiguredSupplier,
        keyPublishedSupplier,
        keyCountersignedSupplier,
        () -> null,
        clock,
        null);
  }

  ClientParticipantStatusPublisher(
      TaktPropertiesHelper taktPropertiesHelper,
      SecurityParticipantDescriptor participantDescriptor,
      Supplier<ObservedPolicySnapshot> observedPolicySupplier,
      BooleanSupplier signingConfiguredSupplier,
      BooleanSupplier keyPublishedSupplier,
      BooleanSupplier keyCountersignedSupplier,
      Supplier<String> currentSigningKeyIdSupplier,
      Clock clock) {
    this(
        taktPropertiesHelper,
        participantDescriptor,
        observedPolicySupplier,
        signingConfiguredSupplier,
        keyPublishedSupplier,
        keyCountersignedSupplier,
        currentSigningKeyIdSupplier,
        clock,
        null);
  }

  /** Test constructor with a pre-built producer and scheduling left to the caller. */
  ClientParticipantStatusPublisher(
      TaktPropertiesHelper taktPropertiesHelper,
      SecurityParticipantDescriptor participantDescriptor,
      Supplier<ObservedPolicySnapshot> observedPolicySupplier,
      BooleanSupplier signingConfiguredSupplier,
      BooleanSupplier keyPublishedSupplier,
      BooleanSupplier keyCountersignedSupplier,
      Clock clock,
      Producer<String, byte[]> producer) {
    this(
        taktPropertiesHelper,
        participantDescriptor,
        observedPolicySupplier,
        signingConfiguredSupplier,
        keyPublishedSupplier,
        keyCountersignedSupplier,
        () -> null,
        clock,
        producer);
  }

  /** Full constructor used by TaktXClient and tests that supply a signing key ID supplier. */
  ClientParticipantStatusPublisher(
      TaktPropertiesHelper taktPropertiesHelper,
      SecurityParticipantDescriptor participantDescriptor,
      Supplier<ObservedPolicySnapshot> observedPolicySupplier,
      BooleanSupplier signingConfiguredSupplier,
      BooleanSupplier keyPublishedSupplier,
      BooleanSupplier keyCountersignedSupplier,
      Supplier<String> currentSigningKeyIdSupplier,
      Clock clock,
      Producer<String, byte[]> producer) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.participantDescriptor = participantDescriptor;
    this.observedPolicySupplier = observedPolicySupplier;
    this.signingConfiguredSupplier = signingConfiguredSupplier;
    this.keyPublishedSupplier = keyPublishedSupplier;
    this.keyCountersignedSupplier = keyCountersignedSupplier;
    this.currentSigningKeyIdSupplier = currentSigningKeyIdSupplier;
    this.clock = clock;
    this.startedAtMs = clock.millis();
    this.participantInstanceId = buildParticipantInstanceId(participantDescriptor.participantId());
    this.producer = producer;
  }

  /**
   * Creates the Kafka producer (if not pre-supplied) and schedules periodic status publication. The
   * first publish happens immediately so the participant becomes visible without waiting a full
   * interval. Calling this more than once is a no-op.
   */
  synchronized void start() {
    if (scheduler != null) {
      return;
    }
    if (producer == null) {
      producer =
          new KafkaProducer<>(
              taktPropertiesHelper.getKafkaProducerProperties(),
              new StringSerializer(),
              new ByteArraySerializer());
    }
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "taktx-client-participant-status");
              thread.setDaemon(true);
              return thread;
            });
    scheduler.scheduleAtFixedRate(
        this::publishCurrentStatusSafely, 0L, PUBLISH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    log.info(
        "Client participant status publisher started — participantInstanceId={} intervalMs={}",
        participantInstanceId,
        PUBLISH_INTERVAL_MS);
  }

  /** Stops the scheduler and closes the producer. Safe to call multiple times. */
  synchronized void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
    if (producer != null) {
      try {
        producer.close();
      } catch (Exception e) {
        log.debug("Error closing participant status producer", e);
      }
      producer = null;
    }
  }

  private void publishCurrentStatusSafely() {
    try {
      publishCurrentStatus();
    } catch (Exception e) {
      log.warn("Client participant status publication failed: {}", e.getMessage());
    }
  }

  /** Evaluates and publishes the current participant status. */
  ParticipantStatusDTO publishCurrentStatus() {
    ParticipantStatusDTO status = evaluateCurrentStatus();
    publish(status);
    return status;
  }

  private void publish(ParticipantStatusDTO status) {
    String topic =
        taktPropertiesHelper.getPrefixedTopicName(Topics.PARTICIPANT_STATUS_TOPIC.getTopicName());
    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(
            topic,
            status.getParticipantInstanceId(),
            ParticipantStatusProtoMapper.toProto(status).toByteArray());
    producer.send(record);
    producer.flush();
    log.debug(
        "Client participant status published: topic={} key={} effectiveState={} readyForDataPlane={}",
        topic,
        record.key(),
        status.getEffectiveState(),
        status.isReadyForDataPlane());
  }

  /**
   * Derives the current participant status from the observed namespace policy and local signing
   * posture. Every started client reports; what the readiness fields mean depends on the declared
   * capabilities.
   */
  ParticipantStatusDTO evaluateCurrentStatus() {
    long nowMs = clock.millis();
    ObservedPolicySnapshot observedPolicy = observedPolicySupplier.get();
    Long observedPolicyVersion =
        observedPolicy != null ? observedPolicy.effectivePolicyVersion() : null;
    Set<ParticipantCapability> capabilities = participantDescriptor.capabilities();

    ParticipantEffectiveState effectiveState;
    boolean readyForDataPlane;
    List<PolicyMismatchReasonDTO> mismatchReasons;

    if (isObserverOnly(capabilities)) {
      // SECURITY_OBSERVER only — present, but not a data-plane participant.
      effectiveState = ParticipantEffectiveState.READY;
      readyForDataPlane = false;
      mismatchReasons = List.of();
    } else if (capabilities.contains(ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER)
        && !capabilities.contains(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
      // AUTHORITATIVE_POLICY_PUBLISHER — control-plane participant, always ready, not data-plane.
      effectiveState = ParticipantEffectiveState.READY;
      readyForDataPlane = false;
      mismatchReasons = List.of();
    } else if (capabilities.contains(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
      SecurityMode effectiveMode =
          observedPolicy != null && observedPolicy.effectiveMode() != null
              ? observedPolicy.effectiveMode()
              : SecurityMode.OPEN;
      boolean signingConfigured = signingConfiguredSupplier.getAsBoolean();
      boolean keyPublished = keyPublishedSupplier.getAsBoolean();
      boolean keyCountersigned = keyCountersignedSupplier.getAsBoolean();

      if (effectiveMode != SecurityMode.ANCHORED) {
        // OPEN mode: the client is ready right now regardless of signing. Report signing gaps as
        // non-blocking informational warnings so operators can see who would be blocked under
        // ANCHORED. An empty list means fully signing-ready — the best case.
        effectiveState = ParticipantEffectiveState.READY;
        readyForDataPlane = true;
        mismatchReasons =
            signingGapReasons(signingConfigured, keyPublished, keyCountersigned, true);
      } else if (signingConfigured && keyPublished && keyCountersigned) {
        // ANCHORED and fully signing-ready.
        effectiveState = ParticipantEffectiveState.READY;
        readyForDataPlane = true;
        mismatchReasons = List.of();
      } else {
        // ANCHORED with signing gaps — now blocking.
        effectiveState = ParticipantEffectiveState.MISMATCH;
        readyForDataPlane = false;
        mismatchReasons =
            signingGapReasons(signingConfigured, keyPublished, keyCountersigned, false);
      }
    } else {
      // No recognised capability — present but not a data-plane participant.
      effectiveState = ParticipantEffectiveState.READY;
      readyForDataPlane = false;
      mismatchReasons = List.of();
    }

    return ParticipantStatusDTO.builder()
        .participantId(participantDescriptor.participantId())
        .participantInstanceId(participantInstanceId)
        .participantKind(participantDescriptor.kind())
        .componentType(participantDescriptor.componentType())
        .capabilities(capabilities)
        .supportedModes(ParticipantStatusSupport.supportedModesForCapabilities(capabilities))
        .namespace(taktPropertiesHelper.getNamespace())
        .startedAt(startedAtMs)
        .lastSeenAt(nowMs)
        .statusExpiresAt(nowMs + STATUS_TTL_MS)
        .statusVerificationLevel(io.taktx.dto.StatusVerificationLevel.LOCALLY_VERIFIED_STATUS)
        .effectiveState(effectiveState)
        .readyForDataPlane(readyForDataPlane)
        .observedPolicyVersion(observedPolicyVersion)
        .observedPolicyHash(observedPolicy != null ? observedPolicy.effectivePolicyHash() : null)
        .mismatchReasons(mismatchReasons)
        .currentSigningKeyId(currentSigningKeyIdSupplier.get())
        .build();
  }

  private static List<PolicyMismatchReasonDTO> signingGapReasons(
      boolean signingConfigured, boolean keyPublished, boolean keyCountersigned, boolean warning) {
    List<PolicyMismatchReasonDTO> reasons = new ArrayList<>();
    if (!signingConfigured) {
      reasons.add(
          reason(
              SIGNATURE_MISSING,
              "No signing identity is configured for this protected runtime participant",
              warning));
    } else if (!keyPublished) {
      reasons.add(
          reason(
              ENGINE_SIGNING_UNAVAILABLE,
              "Signing identity is configured but its public key is not yet visible in the"
                  + " signing-keys registry",
              warning));
    } else if (!keyCountersigned) {
      reasons.add(
          reason(
              ENGINE_KEY_REGISTRATION_SIGNATURE_MISSING,
              "Signing key is published but has no platform registration countersignature",
              warning));
    }
    return List.copyOf(reasons);
  }

  private static PolicyMismatchReasonDTO reason(String code, String message, boolean warning) {
    return PolicyMismatchReasonDTO.builder()
        .code(code)
        .message(message)
        .metadata(Map.of("severity", warning ? "WARNING" : "ERROR"))
        .build();
  }

  private static boolean isObserverOnly(Set<ParticipantCapability> capabilities) {
    return capabilities.size() == 1
        && capabilities.contains(ParticipantCapability.SECURITY_OBSERVER);
  }

  private static String buildParticipantInstanceId(String participantId) {
    return participantId + "@" + resolveHostname() + "#" + ProcessHandle.current().pid();
  }

  private static String resolveHostname() {
    try {
      String hostName = InetAddress.getLocalHost().getHostName();
      if (hostName != null && !hostName.isBlank()) {
        return hostName;
      }
    } catch (Exception e) {
      log.debug("Could not resolve local hostname via InetAddress: {}", e.getMessage());
    }
    String envHost = System.getenv("HOSTNAME");
    if (envHost == null || envHost.isBlank()) {
      envHost = System.getenv("COMPUTERNAME");
    }
    return envHost != null && !envHost.isBlank() ? envHost : "unknown";
  }
}
