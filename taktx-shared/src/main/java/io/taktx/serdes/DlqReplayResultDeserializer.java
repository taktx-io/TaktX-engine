/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.proto.DlqReplayResult;

public class DlqReplayResultDeserializer extends ProtoDeserializer<DlqReplayResult> {

  @Override
  protected Parser<DlqReplayResult> parser() {
    return DlqReplayResult.parser();
  }
}
