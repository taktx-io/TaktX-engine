/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import java.util.UUID;

public class Constants {
  private Constants() {
    // Prevent instantiations
  }

  public static final String EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX = "external-task-trigger-";
  public static final Long MIN_LONG = 0x0000000000000000L;
  public static final Long MAX_LONG = 0xFFFFFFFFFFFFFFFFL;
  public static final Integer MAX_INT = 0xFFFFFFFF;
  public static final UUID MIN_UUID = new UUID(0, 0);
  public static final UUID MAX_UUID = new UUID(0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL);

  /** Kafka record header carrying the Ed25519 signature: {@code "<keyId>.<base64sig>"}. */
  public static final String HEADER_ENGINE_SIGNATURE = "X-TaktX-Signature";

  /**
   * Kafka record header carrying the RS256 JWT issued by the Platform Service — used on inbound
   * commands (start-process, abort, task responses) to identify and authorise the caller.
   */
  public static final String HEADER_AUTHORIZATION = "X-TaktX-Authorization";
}
