/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.*;

/**
 * Cluster-wide engine configuration distributed via the compacted {@code taktx-configuration}
 * topic.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class GlobalConfigurationDTO {
  @Builder.Default private boolean signingEnabled = false;
  @Builder.Default private boolean engineRequiresAuthorization = false;

  /** All key IDs accepted for signature verification. */
  @Builder.Default private List<String> trustedKeyIds = List.of();

  /** Cluster-wide DMN validation strictness. Defaults to preserving current behaviour. */
  @Builder.Default private DmnValidationMode dmnValidationMode = DmnValidationMode.PERMISSIVE;
}
