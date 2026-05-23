/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

/** First-slice readiness/effective-state vocabulary for participant status. */
public enum ParticipantEffectiveState {
  READY,
  NOT_READY,
  MISMATCH,
  STALE
}
