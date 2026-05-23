/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/** Periodically publishes the engine's latest participant security posture. */
@ApplicationScoped
public class ParticipantStatusPublishingMonitor {

  private final ParticipantStatusPublisher participantStatusPublisher;

  public ParticipantStatusPublishingMonitor(ParticipantStatusPublisher participantStatusPublisher) {
    this.participantStatusPublisher = participantStatusPublisher;
  }

  @Scheduled(
      every = "${taktx.security.participant-status-publish-interval:10s}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void publishCurrentStatus() {
    participantStatusPublisher.publishCurrentStatus();
  }
}

