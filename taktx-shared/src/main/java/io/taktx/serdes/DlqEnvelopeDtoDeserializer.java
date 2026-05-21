/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.DlqEnvelope;

/** Deserializes protobuf-backed DLQ envelopes into legacy DTOs. */
public class DlqEnvelopeDtoDeserializer
    extends ProtoDtoDeserializer<DlqEnvelope, io.taktx.proto.DlqEnvelope> {

  public DlqEnvelopeDtoDeserializer() {
    super(DlqEnvelope.class, false);
  }

  @Override
  protected Parser<io.taktx.proto.DlqEnvelope> parser() {
    return io.taktx.proto.DlqEnvelope.parser();
  }

  @Override
  protected DlqEnvelope toDto(io.taktx.proto.DlqEnvelope message) {
    return DlqProtoMapper.toDto(message);
  }
}
