/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import lombok.*;

/** License information with feature flags, distributed via taktx-configuration topic. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString(exclude = "licenseFileContent")
@RegisterForReflection
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class LicenseDTO {
  private String licenseFileContent;
  private String licensee;
  private Instant issuedAt;
  private Instant expiresAt;
  @Builder.Default private boolean signingAllowed = false;
  @Builder.Default private boolean encryptionAllowed = false;
  @Builder.Default private boolean rbacAllowed = false;
  @Builder.Default private int maxPartitionsPerTopic = 3;
}
