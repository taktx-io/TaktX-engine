/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.SecurityEventDTO;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Public observability facade for participant and key visibility — participant-status snapshots,
 * recent security events, and polling helpers suitable for integration tests and Console display.
 *
 * <p>Namespace mode is startup-static; it is published to {@code taktx-security-policy} by the
 * engine and may be read directly from that topic. This facade focuses on runtime readiness state.
 */
public class SecurityObservabilityClient {

  private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(50);
  private static final String CONSUMER_ARGUMENT = "consumer";
  private static final String PREDICATE_ARGUMENT = "predicate";

  private final Supplier<Map<String, ParticipantStatusDTO>> participantStatusSnapshotSupplier;
  private final Supplier<List<SecurityEventDTO>> recentSecurityEventsSupplier;
  private final ConsumerRegistrars consumerRegistrars;
  private final Runnable initializer;
  private final Duration pollInterval;

  SecurityObservabilityClient(
      Supplier<Map<String, ParticipantStatusDTO>> participantStatusSnapshotSupplier,
      Supplier<List<SecurityEventDTO>> recentSecurityEventsSupplier,
      ConsumerRegistrars consumerRegistrars,
      Runnable initializer) {
    this(
        participantStatusSnapshotSupplier,
        recentSecurityEventsSupplier,
        consumerRegistrars,
        initializer,
        DEFAULT_POLL_INTERVAL);
  }

  SecurityObservabilityClient(
      Supplier<Map<String, ParticipantStatusDTO>> participantStatusSnapshotSupplier,
      Supplier<List<SecurityEventDTO>> recentSecurityEventsSupplier,
      ConsumerRegistrars consumerRegistrars,
      Runnable initializer,
      Duration pollInterval) {
    this.participantStatusSnapshotSupplier =
        Objects.requireNonNull(
            participantStatusSnapshotSupplier, "participantStatusSnapshotSupplier");
    this.recentSecurityEventsSupplier =
        Objects.requireNonNull(recentSecurityEventsSupplier, "recentSecurityEventsSupplier");
    this.consumerRegistrars = Objects.requireNonNull(consumerRegistrars, "consumerRegistrars");
    this.initializer = Objects.requireNonNull(initializer, "initializer");
    if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
      throw new IllegalArgumentException("pollInterval must be > 0");
    }
    this.pollInterval = pollInterval;
  }

  /** Returns the latest unexpired participant-status snapshot keyed by participant instance ID. */
  public Map<String, ParticipantStatusDTO> getParticipantStatusSnapshot() {
    ensureInitialized();
    Map<String, ParticipantStatusDTO> snapshot = participantStatusSnapshotSupplier.get();
    if (snapshot == null || snapshot.isEmpty()) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
  }

  /** Returns the bounded recent security-event history. */
  public List<SecurityEventDTO> getRecentSecurityEvents() {
    ensureInitialized();
    List<SecurityEventDTO> events = recentSecurityEventsSupplier.get();
    return events == null || events.isEmpty() ? List.of() : List.copyOf(events);
  }

  /** Returns a combined posture snapshot from participant-status and security-event data. */
  public SecurityPostureSnapshot getPostureSnapshot() {
    return SecurityPostureSnapshot.from(getParticipantStatusSnapshot(), getRecentSecurityEvents());
  }

  /** Registers a callback and immediately replays the current participant-status snapshot. */
  public void registerParticipantStatusConsumer(ParticipantStatusConsumer consumer) {
    Objects.requireNonNull(consumer, CONSUMER_ARGUMENT);
    ensureInitialized();
    consumerRegistrars.participantStatusConsumerRegistrar().accept(consumer);
    consumer.accept(getParticipantStatusSnapshot());
  }

  /** Registers a callback and immediately replays the bounded recent event history. */
  public void registerSecurityEventConsumer(SecurityEventConsumer consumer) {
    Objects.requireNonNull(consumer, CONSUMER_ARGUMENT);
    ensureInitialized();
    consumerRegistrars.securityEventConsumerRegistrar().accept(consumer);
    for (SecurityEventDTO event : getRecentSecurityEvents()) {
      consumer.accept(event);
    }
  }

  /** Finds a matching event in the current bounded event history. */
  public Optional<SecurityEventDTO> findRecentSecurityEvent(Predicate<SecurityEventDTO> predicate) {
    Objects.requireNonNull(predicate, PREDICATE_ARGUMENT);
    return getRecentSecurityEvents().stream().filter(predicate).findFirst();
  }

  /** Polls until the participant-status snapshot satisfies the supplied predicate. */
  public Map<String, ParticipantStatusDTO> awaitParticipantStatusSnapshot(
      Predicate<Map<String, ParticipantStatusDTO>> predicate, Duration timeout) {
    Objects.requireNonNull(predicate, PREDICATE_ARGUMENT);
    return awaitSnapshot(
        "participant status snapshot", this::getParticipantStatusSnapshot, predicate, timeout);
  }

  /** Polls until the combined posture snapshot satisfies the supplied predicate. */
  public SecurityPostureSnapshot awaitPostureSnapshot(
      Predicate<SecurityPostureSnapshot> predicate, Duration timeout) {
    Objects.requireNonNull(predicate, PREDICATE_ARGUMENT);
    return awaitSnapshot("security posture snapshot", this::getPostureSnapshot, predicate, timeout);
  }

  /** Polls until a matching security event appears in the bounded recent event history. */
  public SecurityEventDTO awaitSecurityEvent(
      Predicate<SecurityEventDTO> predicate, Duration timeout) {
    Objects.requireNonNull(predicate, PREDICATE_ARGUMENT);
    requirePositiveTimeout(timeout);
    ensureInitialized();
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      Optional<SecurityEventDTO> matchingEvent = findRecentSecurityEvent(predicate);
      if (matchingEvent.isPresent()) {
        return matchingEvent.get();
      }
      if (Thread.currentThread().isInterrupted()) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for security event");
      }
      long remainingNanos = deadline - System.nanoTime();
      if (remainingNanos <= 0L) {
        throw new IllegalStateException("Timed out waiting for security event within " + timeout);
      }
      LockSupport.parkNanos(Math.min(pollInterval.toNanos(), remainingNanos));
    }
  }

  private <T> T awaitSnapshot(
      String description, Supplier<T> snapshotSupplier, Predicate<T> predicate, Duration timeout) {
    requirePositiveTimeout(timeout);
    ensureInitialized();
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      T snapshot = snapshotSupplier.get();
      if (predicate.test(snapshot)) {
        return snapshot;
      }
      if (Thread.currentThread().isInterrupted()) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for " + description);
      }
      long remainingNanos = deadline - System.nanoTime();
      if (remainingNanos <= 0L) {
        throw new IllegalStateException(
            "Timed out waiting for " + description + " within " + timeout);
      }
      LockSupport.parkNanos(Math.min(pollInterval.toNanos(), remainingNanos));
    }
  }

  private void ensureInitialized() {
    initializer.run();
  }

  private static void requirePositiveTimeout(Duration timeout) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be > 0");
    }
  }

  record ConsumerRegistrars(
      Consumer<NamespaceSecurityPolicyConsumer> namespaceSecurityPolicyConsumerRegistrar,
      Consumer<ParticipantStatusConsumer> participantStatusConsumerRegistrar,
      Consumer<SecurityEventConsumer> securityEventConsumerRegistrar) {}
}
