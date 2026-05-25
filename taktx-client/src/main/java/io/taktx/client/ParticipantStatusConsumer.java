/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.ParticipantStatusDTO;
import java.util.Map;

/** Callback for current participant-status snapshots observed from the public status topic. */
@FunctionalInterface
public interface ParticipantStatusConsumer {
  void accept(Map<String, ParticipantStatusDTO> statuses);
}
