/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.annotation;

/** Acknowledgment strategies for message processing. */
public enum AckStrategy {
  /** The framework acknowledges automatically when the handler returns successfully. */
  IMPLICIT,
  /** The handler acknowledges offsets in batches under its own control. */
  EXPLICIT_BATCH,
  /** The handler acknowledges each message explicitly. */
  EXPLICIT_MESSAGE
}
