/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi;

import io.taktx.dto.ProcessInstanceTriggerDTO;
import jakarta.annotation.Nullable;

public record ProcessInstanceTriggerEnvelope(
    ProcessInstanceTriggerDTO trigger,
    boolean signatureVerified,
    @Nullable String signatureKeyId,
    @Nullable String signatureError) {

  public ProcessInstanceTriggerEnvelope(
      ProcessInstanceTriggerDTO trigger,
      boolean signatureVerified,
      @Nullable String signatureKeyId) {
    this(trigger, signatureVerified, signatureKeyId, null);
  }

  public boolean hasSignatureError() {
    return signatureError != null && !signatureError.isBlank();
  }
}
