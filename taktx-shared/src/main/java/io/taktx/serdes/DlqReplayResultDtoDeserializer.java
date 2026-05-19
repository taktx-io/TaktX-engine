/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.DlqReplayResult;

/** Deserializes protobuf-backed DLQ replay results into legacy DTOs. */
public class DlqReplayResultDtoDeserializer
    extends ProtoDtoDeserializer<DlqReplayResult, io.taktx.proto.DlqReplayResult> {

  public DlqReplayResultDtoDeserializer() {
    super(DlqReplayResult.class, false);
  }

  @Override
  protected Parser<io.taktx.proto.DlqReplayResult> parser() {
    return io.taktx.proto.DlqReplayResult.parser();
  }

  @Override
  protected DlqReplayResult toDto(io.taktx.proto.DlqReplayResult message) {
    return DlqProtoMapper.toDto(message);
  }
}
