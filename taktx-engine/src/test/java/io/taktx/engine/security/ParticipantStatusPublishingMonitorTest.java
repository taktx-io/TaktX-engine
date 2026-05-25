/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ParticipantStatusPublishingMonitorTest {

  @Test
  void publishCurrentStatus_delegatesToPublisher() {
    ParticipantStatusPublisher publisher = Mockito.mock(ParticipantStatusPublisher.class);
    ParticipantStatusPublishingMonitor monitor = new ParticipantStatusPublishingMonitor(publisher);

    monitor.publishCurrentStatus();

    verify(publisher).publishCurrentStatus();
  }
}
