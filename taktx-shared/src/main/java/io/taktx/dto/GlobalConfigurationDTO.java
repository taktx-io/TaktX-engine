/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
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
  @Builder.Default private boolean authorizationEnabled = false;

  /** All key IDs accepted for signature verification. */
  @Builder.Default private List<String> trustedKeyIds = List.of();
}
