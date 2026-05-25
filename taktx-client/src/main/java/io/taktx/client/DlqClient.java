/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import java.util.Objects;
import java.util.function.Consumer;

/** Focused public facet for DLQ observation and replay operations. */
public final class DlqClient {

  private final TaktXClient client;

  DlqClient(TaktXClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  public void registerDlqEntryConsumer(String groupId, Consumer<DlqEnvelope> handler) {
    client.registerDlqEntryConsumer(groupId, handler);
  }

  public void registerDlqEntryConsumer(
      String groupId, Consumer<DlqEnvelope> handler, boolean startFromEarliest) {
    client.registerDlqEntryConsumer(groupId, handler, startFromEarliest);
  }

  public void submitReplayCommand(DlqReplayCommand command) {
    client.submitReplayCommand(command);
  }

  public void registerReplayResultConsumer(String groupId, Consumer<DlqReplayResult> handler) {
    client.registerReplayResultConsumer(groupId, handler);
  }
}
