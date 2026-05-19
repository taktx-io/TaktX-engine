/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.DlqReplayCommand;

/** Deserializes protobuf-backed DLQ replay commands into legacy DTOs. */
public class DlqReplayCommandDtoDeserializer
    extends ProtoDtoDeserializer<DlqReplayCommand, io.taktx.proto.DlqReplayCommand> {

  public DlqReplayCommandDtoDeserializer() {
    super(DlqReplayCommand.class, false);
  }

  @Override
  protected Parser<io.taktx.proto.DlqReplayCommand> parser() {
    return io.taktx.proto.DlqReplayCommand.parser();
  }

  @Override
  protected DlqReplayCommand toDto(io.taktx.proto.DlqReplayCommand message) {
    return DlqProtoMapper.toDto(message);
  }
}
