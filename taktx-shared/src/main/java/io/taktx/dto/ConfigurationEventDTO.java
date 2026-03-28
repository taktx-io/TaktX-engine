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
