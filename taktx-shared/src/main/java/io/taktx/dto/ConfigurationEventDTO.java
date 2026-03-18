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

/** Wrapper for events published to the taktx-configuration compacted topic. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class ConfigurationEventDTO {

  public enum ConfigurationEventType {
    CONFIGURATION_UPDATE,
    LICENSE_UPDATE,
    COMBINED_UPDATE
  }

  private ConfigurationEventType eventType;
  private GlobalConfigurationDTO configuration;
  private Instant timestamp;
  private String publishedByInstance;
}
