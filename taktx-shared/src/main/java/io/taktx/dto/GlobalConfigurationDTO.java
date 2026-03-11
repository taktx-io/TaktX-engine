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
 * topic. The Kafka record key is {@code "config"}.
 *
 * <h3>Key fields for zero-downtime rotation</h3>
 *
 * <ul>
 *   <li>{@code signingKeyId} — the single key ID currently used to sign new outgoing messages
 *   <li>{@code trustedKeyIds} — all key IDs accepted for verification, including {@code
 *       signingKeyId} plus any {@code TRUSTED} keys still within their drain window
 * </ul>
 *
 * <p>Consumers must verify against all {@code trustedKeyIds}. Verifying only against {@code
 * signingKeyId} would cause rejections for in-flight messages during a rotation.
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
  @Builder.Default private boolean rbacEnabled = false;

  /** The key ID currently used to sign outgoing engine messages. */
  private String signingKeyId;

  /**
   * All key IDs accepted for signature verification. Includes {@code signingKeyId} plus any
   * previously ACTIVE keys still within their TRUSTED drain window.
   */
  @Builder.Default private List<String> trustedKeyIds = List.of();
}
