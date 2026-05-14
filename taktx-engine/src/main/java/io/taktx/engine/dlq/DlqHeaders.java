/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

/**
 * Canonical Kafka record-header keys used to annotate DLQ entries with rejection metadata.
 *
 * <p>All processors that build a {@code Map<String, byte[]>} header snapshot before creating a
 * {@link io.taktx.dto.DlqEntryDTO} must reference these constants rather than duplicating the
 * literal strings. {@link DlqPublisher} reads the same keys when deriving the {@link
 * io.taktx.dto.DlqReasonCode}, reason text, and {@link io.taktx.dto.DlqCaptureStage} from a DLQ
 * entry.
 */
public final class DlqHeaders {

  /**
   * Header whose value is the {@link io.taktx.dto.DlqReasonCode} name (e.g. {@code
   * REPLAY_DETECTED}).
   */
  public static final String REASON_HINT = "X-TaktX-DLQ-Reason-Hint";

  /** Header whose value is a human-readable description of the rejection. */
  public static final String REASON_TEXT = "X-TaktX-DLQ-Reason-Text";

  /**
   * Header whose value is the {@link io.taktx.dto.DlqCaptureStage} name (e.g. {@code PROCESSOR}).
   */
  public static final String CAPTURE_STAGE = "X-TaktX-DLQ-Capture-Stage";

  private DlqHeaders() {}
}
